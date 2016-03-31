#!/bin/bash
set -eu
#For reference json output we get when we try to gets from the repository is similar to:
# {
#  "x1.y1.z1": "blahblah", "x2.y2.z2": "moreblahblah", "latest": "someblahblah"
#  and so on...
# }

LOADER="aerofs/loader"
SA_LOADER="aerofs/sa-loader"

# Create a version directory if it doesn't exist. Create two files in it
# to track versions of loader and sa-loader respectively. In each of these files
# we will append versions of the loader/sa-loader donwloaded respectively and remove
# versions that have been garbage cleaned.
VERSIONS_DIR="/data/versions"
mkdir -p ${VERSIONS_DIR}
LOADER_VERSION_FILE="$VERSIONS_DIR/loader-versions"
SA_LOADER_VERSION_FILE="$VERSIONS_DIR/sa-loader-versions"

REPO="registry.aerofs.com"
FILE="/tmp/tags"
REGISTRY_PORT=5000

cmd_with_retry() {
    cmd="$1"
    (set +e
        RETRY=0
        TIMEOUT=1
        while true; do
            $cmd
            if [ $? = 0 ]; then
                break
            elif [ ${RETRY} = 6 ]; then
                echo "ERROR: Retried too many times. I gave up."
                exit 22
            else
                echo "CMD: $cmd. Retry attempt #${RETRY} in ${TIMEOUT} seconds..."
                sleep ${TIMEOUT}
                TIMEOUT=$[TIMEOUT * 2]
                RETRY=$[RETRY + 1]
            fi
        done
    )
}


pull_push () {
    local LOADER_IMG="$1"
    local VERSION="$2"
    local VERSION_FILE="$3"
    local IMAGES="$4"

    # Pull all images passed in
    for i in ${IMAGES}; do
        local IMAGE_FULL_NAME=${REPO}/${i}:$VERSION
        echo "Pulling image: $IMAGE_FULL_NAME"
        cmd_with_retry "docker pull ${IMAGE_FULL_NAME}"
    done

    # Tag all images from localhost registry and push to localhost registry.
    for i in ${IMAGES}; do
        local PUSH_IMAGE="localhost:${REGISTRY_PORT}/$i:$VERSION"
        docker tag -f "$REPO/${i}:$VERSION" "${PUSH_IMAGE}"
        echo "Pushing $PUSH_IMAGE to localhost registry"
        cmd_with_retry "docker push ${PUSH_IMAGE}"
    done

    # Push loader at the end.
    local LOADER_PUSH_IMAGE="localhost:${REGISTRY_PORT}/$LOADER_IMG"
    docker tag -f "$REPO/${LOADER_IMG}:$VERSION" ${LOADER_PUSH_IMAGE}
    echo "Push $LOADER_IMG:latest to localhost registry"
    cmd_with_retry "docker push ${LOADER_PUSH_IMAGE}"

    # Append version to versions file.
    echo "${VERSION}" >> ${VERSION_FILE}
}


delete_manifests() {
    local IMAGES="$1"
    local VERSIONS_TO_DELETE="$2"
    local VERSIONS_FILE="$3"

    # For all images passed in, get all their tags. For all tags except "latest" or latest tag delete manifests
    # so that garbage cleaning removes those images from disk later.
    for version in $VERSIONS_TO_DELETE; do
        can_remove_version=1
        for img in ${IMAGES}; do
            echo "Delete $img:$version"
           # Remove manifests
            manifests_header=$(curl -I -s -w "HTTP_CODE: %{http_code}" -H "Accept: application/vnd.docker.distribution.manifest.v2+json" \
                http://registry.service:${REGISTRY_PORT}/v2/$img/manifests/$version)
            if [[ $? == 0 ]]
            then
                # printf because its a multi line variable
                status_code=$(printf %s "$manifests_header" | grep "HTTP_CODE" | awk '{print $NF}')
                # If we cannot find the image, then there's nothing to delete.
                if [[ $status_code != 200 ]]
                then
                    echo "WARNING: Failed to get manifest for $img:$version. HTTP status code: $status_code"
                    continue
                fi

                digest_value=$(printf %s "$manifests_header" | grep "Docker-Content-Digest" | awk '{print $NF}')
                status_code=$(curl -s -o /dev/null -I -w "%{http_code}" -X DELETE http://registry.service:$REGISTRY_PORT/v2/$img/manifests/$digest_value)
                if [[ $status_code == 202 ]]
                then
                    # Explicit docker rmi so that docker images output is clean. Strictly speaking this is independent of us
                    # being able to remove manifests/clean the registry. However, to avoid confusion and maintain consistency
                    # with registry state, do this only if able to delete manifests from registry.
                    docker rmi $REPO/$img:$version
                    docker rmi localhost:${REGISTRY_PORT}/$img:$version
                    echo "Removed manifest for $img:$version"
                else
                    echo "WARNING: Failed to delete manifest for $img:$version. HTTP status code: $status_code"
                    can_remove_version=0
                fi
            else
                echo "WARNING: Unable to get digest for image $img:$version"
                can_remove_version=0
            fi
        done

        if [[ ${can_remove_version} == 1 ]]
        then
            sed -i "/$version/d" ${VERSIONS_FILE}
        fi
    done
}


version () {
    # Get the json output and replace all "," by /n to get one version per line
    tags=$(curl -s https://${REPO}/v1/repositories/$1/tags)
    tr , '\n' <<<"$tags" > $FILE

    # Now get version of "latest"
    latest_ver_img_id=$(cat $FILE | grep "latest" | awk '{print $NF'} | grep -o '".*"' | tr -d '"')

    # 1. Now get the other version that matches version of "latest".
    # 2. Filter out "latest" to get the actual latest version
    # 3. Remove all double quotes and : from result of 2
    version=$(cat $FILE | grep $latest_ver_img_id | grep -v "latest" |awk '{print $1}' | sed -e 's/"\(.*\)":/\1/g')
    if [[ -z $version ]]
    then
       echo "Unable to obtain latest version of $1. Exiting"
       exit 1
    fi
    echo "$version"

}

# IMPORTANT: We only delete previous versions that are atleast 2 updates old i.e.
# if latest version is 1.x, only delete versions that <= 1.x-2. Why? Consider the
# scenario where we just downloaded version 1.x and now going to delete/garbage clean
# 1.x-1. However, its quite possible that the appliance is upgrading to 1.x-1 concurrently.
# So to avoid ugly race conditions, maintain a version file to which we append
# downloaded versions and only remove version upto 1.x-2(i.e. lines 1 - (n - 2) where n = wc -l versions_file)
get_versions_to_delete_from_version_file() {
    local versions_file="$1"
    local num_versions_known="$(wc -l < ${versions_file})"
    if [[ ${num_versions_known} -le 2 ]]
    then
        echo ""
    else
        echo "$(head -n $(($num_versions_known-2)) ${versions_file})"
    fi
}


# Get version of loader to pull from registry.aerofs.com
LOADER_VERSION="$(version $LOADER)"
echo "Pulling loader version $LOADER_VERSION"
LOADER_FULL_NAME=${REPO}/${LOADER}:${LOADER_VERSION}
LOADER_IMAGES="$(docker run --rm -v /var/run/docker.sock:/var/run/docker.sock ${LOADER_FULL_NAME} images)"


pull_push ${LOADER} ${LOADER_VERSION} ${LOADER_VERSION_FILE} "${LOADER_IMAGES}"

# Get version of sa-loader to pull from registry.aerofs.com
SA_LOADER_VERSION="$(version $SA_LOADER)"
echo "Pulling SA-loader version $SA_LOADER_VERSION"
LOADER_FULL_NAME=${REPO}/${SA_LOADER}:${SA_LOADER_VERSION}
SA_LOADER_IMAGES="$(docker run --rm -v /var/run/docker.sock:/var/run/docker.sock ${LOADER_FULL_NAME} images)"

pull_push ${SA_LOADER} ${SA_LOADER_VERSION} ${SA_LOADER_VERSION_FILE} "${SA_LOADER_IMAGES}"

# Explicitly set +e here because failure to delete/garbage clean is not really a fatal failure.
set +e

versions_to_delete="$(get_versions_to_delete_from_version_file $LOADER_VERSION_FILE)"
[[ -z "${versions_to_delete}" ]] || delete_manifests "$LOADER_IMAGES" "${versions_to_delete}" "${LOADER_VERSION_FILE}"

versions_to_delete="$(get_versions_to_delete_from_version_file $SA_LOADER_VERSION_FILE)"
[[ -z "${versions_to_delete}" ]] || delete_manifests "$SA_LOADER_IMAGES" "${versions_to_delete}" "${SA_LOADER_VERSION_FILE}"

REGISTRY_CONTAINER=$(docker ps | grep 'registry.hub.docker.com/aerofs/registry-mirror.registry:' | awk '{print $1}')
docker exec $REGISTRY_CONTAINER sh -c "registry garbage-collect /etc/docker/registry/config.yml"
