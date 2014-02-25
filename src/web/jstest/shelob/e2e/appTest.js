angular.module('shelobAppTest', ['ngMockE2E', 'shelobApp'])
.run(function($httpBackend) {
    // these tests assume a daemon responds with the following AeroFS folder:
    //
    // empty_folder/
    // other_folder/
    //   textfile.txt
    //   otherfile.txt
    // website.html
    var empty_folder_obj = {id:"01a01a01a", name:"empty_folder", is_shared:false};
    var other_folder_obj = {id:"9f89f89f8", name:"other_folder", is_shared:false};
    var website_html_obj = {id:"46d46d46d", name:"website.html", last_modified: "2013-12-14T02:19:59Z"};
    var textfile_txt_obj = {id:"23b23b23b", name:"textfile.txt", last_modified: "2013-12-14T02:19:59Z"};
    var otherfile_txt_obj = {id:"67e67e67e", name:"otherfile.txt", last_modified: "2013-12-14T02:19:59Z"};

    // mock out the API responses according to the folder structure describe above
    $httpBackend.whenGET('/api/v1.0/children/').respond(
        {
            parent: "",
            folders: [empty_folder_obj, other_folder_obj],
            files: [website_html_obj]
        }
    );
    $httpBackend.whenGET('/api/v1.0/children/01a01a01a').respond(
        {
            parent: "01a01a01a",
            folders: [],
            files: [],
        }
    );
    $httpBackend.whenGET('/api/v1.0/children/9f89f89f8').respond(
        {
            parent: "9f89f89f8",
            folders: [],
            files: [textfile_txt_obj, otherfile_txt_obj],
        }
    );

    // mock out the /json_token backend service
    $httpBackend.whenGET('/json_token').respond(
        {token: 'tokentokentoken'}
    );

    // pass through all other requests
    $httpBackend.whenGET().passThrough();
});
