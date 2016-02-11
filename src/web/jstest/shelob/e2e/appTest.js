angular.module('shelobAppTest', ['ngMockE2E', 'shelobApp'])
.run(function($httpBackend, $q, API) {
    // these tests assume a daemon responds with the following AeroFS folder:
    //
    // empty_folder/
    // other_folder/
    //   deeper_folder/
    //   textfile.txt
    //   otherfile
    // website.html
    
    enableLinksharing = true;
    currentUser = '';
    var root_folder_obj = {id:"root", name:"AeroFS", is_shared:false};
    var empty_folder_obj = {id:"01a01a01a", name:"empty_folder", is_shared:false};
    var other_folder_obj = {id:"9f89f89f8", name:"other_folder", is_shared:false};
    var deeper_folder_obj = {id:"78f78f78f", name:"deeper_folder", is_shared:false};
    var website_html_obj = {id:"46d46d46d", name:"website.html", last_modified: "2013-12-14T02:19:59Z", mime_type: "text/plain"};
    var textfile_txt_obj = {id:"23b23b23b", name:"textfile.txt", last_modified: "2013-12-14T02:19:59Z", mime_type: "text/plain"};
    var otherfile_obj = {id:"67e67e67e", name:"otherfile", last_modified: "2013-12-14T02:19:59Z", mime_type: "not/a/mime/type"};
    var anotherfile_obj = {id:"42b42b42b", name:"anotherfile", last_modified: "2013-12-14T02:19:59Z", mime_type: "not/a/mime/type"};

    // mock out the API responses according to the folder structure describe above
    sinon.stub(API.folder, 'getMetadata', function(id, fields) {
      switch (id) {
        case 'root':
          return $q.when({data : {
            name: 'AeroFS',
            id: 'root',
            path: {folders: []},
            children: {
                folders: [empty_folder_obj, other_folder_obj],
                files: [website_html_obj]
            }
          }});
          break;
        case '01a01a01a': 
          return $q.when( {data : {
            name: empty_folder_obj.name,
            id: empty_folder_obj.id,
            is_shared: empty_folder_obj.is_shared,
            path: {folders: [root_folder_obj]},
            children: {
                folders: [],
                files: [],
            }
          }});
          break;
        case '9f89f89f8':
          return $q.when({data : {
            name: other_folder_obj.name,
            id: other_folder_obj.id,
            path: {folders: [root_folder_obj]},
            children: {
                folders: [deeper_folder_obj],
                files: [textfile_txt_obj, otherfile_obj, anotherfile_obj],
            }
          }});
          break;
        case '78f78f78f':
          return $q.when({ data : {
            name: deeper_folder_obj.name,
            id: deeper_folder_obj.id,
            path: {folders: [root_folder_obj, other_folder_obj]},
            children: {
                folders: [],
                files: [],
            }
          }});
        }
    });
 
    sinon.stub(API, 'getLinks',function(sid, headers) {
      return  $q.when({ data : { urls : []}});
    });
   
    // mock out the /json_token backend service
    $httpBackend.whenPOST(/\/json_token(\?t=[01]?.?\d*)?/).respond(
        {token: 'tokentokentoken'}
    );

    $httpBackend.whenGET().passThrough();
});
