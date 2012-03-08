##
# git-review checks whitespace and pushes the current branch to Gerrit for review
#
# usage: git-review [remote branch]
##

# Currently only operates on a fixed remote
REMOTE="origin"

REMOTE_BRANCH="$1"
if [ -z "$REMOTE_BRANCH" ]; then
    # No remote branch given
    # Extract the current fully qualified branch name (ex: refs/heads/master)
    # so that we can look up the remote tracking branch
    BRANCH=`git symbolic-ref HEAD 2> /dev/null`
    if [ "$?" != "0" ]; then
        echo "Not on a valid branch" 1>&2
        exit 2
    fi

    # Find the remote branch this branch is tracking
    REMOTE_BRANCH=`git for-each-ref --format='%(upstream:short)' $BRANCH | grep "^origin/" | cut -c 8-`
    if [ -z "$REMOTE_BRANCH" ]; then
        echo "Not tracking a remote branch" 1>&2
        exit 3
    fi
fi

# Check for whitespace errors. Compare against upstream commit
git diff $REMOTE/$REMOTE_BRANCH HEAD --check
if [ "$?" != "0" ]; then
    echo "Please fix whitespace errors before submitting patch set" 1>&2
    exit 1
fi

git push $REMOTE HEAD:refs/for/$REMOTE_BRANCH
