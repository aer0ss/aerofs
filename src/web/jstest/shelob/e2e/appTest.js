angular.module('shelobAppTest', ['ngMockE2E', 'shelobApp'])
.run(function($httpBackend) {
    // these tests assume a daemon responds with the following AeroFS folder:
    //
    // empty_folder/
    // other_folder/
    //   deeper_folder/
    //   textfile.txt
    //   otherfile
    // website.html
    
    enableLinksharing = true;
    var root_folder_obj = {id:"root", name:"AeroFS", is_shared:false};
    var empty_folder_obj = {id:"01a01a01a", name:"empty_folder", is_shared:false};
    var other_folder_obj = {id:"9f89f89f8", name:"other_folder", is_shared:false};
    var deeper_folder_obj = {id:"78f78f78f", name:"deeper_folder", is_shared:false};
    var website_html_obj = {id:"46d46d46d", name:"website.html", last_modified: "2013-12-14T02:19:59Z", mime_type: "text/plain"};
    var textfile_txt_obj = {id:"23b23b23b", name:"textfile.txt", last_modified: "2013-12-14T02:19:59Z", mime_type: "text/plain"};
    var otherfile_obj = {id:"67e67e67e", name:"otherfile", last_modified: "2013-12-14T02:19:59Z", mime_type: "not/a/mime/type"};
    var anotherfile_obj = {id:"42b42b42b", name:"anotherfile", last_modified: "2013-12-14T02:19:59Z", mime_type: "not/a/mime/type"};

    // mock out the API responses according to the folder structure describe above
    $httpBackend.whenGET(/^\/api\/v1.2\/folders\/root\/?\?fields=children,path(&.*)?$/).respond(
        {
            name: 'AeroFS',
            id: 'root',
            path: {folders: []},
            children: {
                folders: [empty_folder_obj, other_folder_obj],
                files: [website_html_obj]
            }
        }
    );
    $httpBackend.whenGET(/^\/api\/v1.2\/folders\/01a01a01a\/?\?fields=children,path(&.*)?$/).respond(
        {
            name: empty_folder_obj.name,
            id: empty_folder_obj.id,
            is_shared: empty_folder_obj.is_shared,
            path: {folders: [root_folder_obj]},
            children: {
                folders: [],
                files: [],
            }
        }
    );
    $httpBackend.whenGET(/^\/api\/v1.2\/folders\/9f89f89f8\/?\?fields=children,path(&.*)?$/).respond(
        {
            name: other_folder_obj.name,
            id: other_folder_obj.id,
            path: {folders: [root_folder_obj]},
            children: {
                folders: [deeper_folder_obj],
                files: [textfile_txt_obj, otherfile_obj, anotherfile_obj],
            }
        }
    );
    $httpBackend.whenGET(/^\/api\/v1.2\/folders\/78f78f78f\/?\?fields=children,path(&.*)?$/).respond(
        {
            name: deeper_folder_obj.name,
            id: deeper_folder_obj.id,
            path: {folders: [root_folder_obj, other_folder_obj]},
            children: {
                folders: [],
                files: [],
            }
        }
    );

    $httpBackend.whenGET(/^\/list_urls_for_store\?sid=(.*)?$/).respond(
        {
            urls: []
        }
    );

    // mock out the /json_token backend service
    $httpBackend.whenGET(/\/json_token(\?t=[01]?.?\d*)?/).respond(
        {token: 'tokentokentoken'}
    );

    // pass through all other requests
    $httpBackend.whenGET().passThrough();
});
