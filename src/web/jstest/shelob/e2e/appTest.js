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
    var empty_folder_obj = {id:"01a01a01a", name:"empty_folder", is_shared:false};
    var other_folder_obj = {id:"9f89f89f8", name:"other_folder", is_shared:false};
    var deeper_folder_obj = {id:"78f78f78f", name:"deeper_folder", is_shared:false};
    var website_html_obj = {id:"46d46d46d", name:"website.html", last_modified: "2013-12-14T02:19:59Z", mime_type: "text/plain"};
    var textfile_txt_obj = {id:"23b23b23b", name:"textfile.txt", last_modified: "2013-12-14T02:19:59Z", mime_type: "text/plain"};
    var otherfile_obj = {id:"67e67e67e", name:"otherfile", last_modified: "2013-12-14T02:19:59Z", mime_type: "not/a/mime/type"};

    // mock out the API responses according to the folder structure describe above
    $httpBackend.whenGET('/api/v1.0/folders/01a01a01a').respond(empty_folder_obj);
    $httpBackend.whenGET('/api/v1.0/folders/9f89f89f8').respond(other_folder_obj);
    $httpBackend.whenGET('/api/v1.0/folders/78f78f78f').respond(deeper_folder_obj);
    $httpBackend.whenGET(/^\/api\/v1.0\/children\/?(\?.*)?$/).respond(
        {
            parent: "",
            folders: [empty_folder_obj, other_folder_obj],
            files: [website_html_obj]
        }
    );
    $httpBackend.whenGET(/^\/api\/v1.0\/children\/01a01a01a\/?(\?.*)?$/).respond(
        {
            parent: "01a01a01a",
            folders: [],
            files: [],
        }
    );
    $httpBackend.whenGET(/^\/api\/v1.0\/children\/9f89f89f8\/?(\?.*)?$/).respond(
        {
            parent: "9f89f89f8",
            folders: [deeper_folder_obj],
            files: [textfile_txt_obj, otherfile_obj],
        }
    );
    $httpBackend.whenGET(/^\/api\/v1.0\/children\/78f78f78f\/?(\?.*)?$/).respond(
        {
            parent: "78f78f78f",
            folders: [],
            files: [],
        }
    );

    // mock out the /json_token backend service
    $httpBackend.whenGET('/json_token').respond(
        {token: 'tokentokentoken'}
    );

    // pass through all other requests
    $httpBackend.whenGET().passThrough();
});
