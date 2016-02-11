describe('Shelob Controllers', function() {

    var httpProvider;
    
    beforeEach(module('shelobControllers'));
    beforeEach(module('shelobServices', function($httpProvider) {
        /*
         * Each time an API call is made, make sure $scope.outstandingRequests
         * is non-zero so that the spinner will show.
         */
        $httpProvider.interceptors.push(function ($q, $rootScope, $timeout) {
            return {
                'request': function (config) {
                    // do the check in a timeout so force AngularJS to $digest, which
                    // will sync the $rootScope.outstandingRequests $watch
                    $timeout(function () {
                        expect($rootScope.outstandingRequests).toBeGreaterThan(0);
                    }, 0);
                    return config || $q.when(config);
                }
            };
        });
    }));
   
    describe('FileListCtrl', function() {

        var $httpBackend, $rootScope, $controller, $q, API, routeParams, modal, FileListCtrl;
        var modalObject;
        var folder_1, folder_2, file, folder_1_object, folder_2_object, file_object;
        enableLinksharing = true;

        function get_object_by_id(id) {
            for (var i = 0; i < $rootScope.objects.length; i++) {
                if ($rootScope.objects[i].id == id) {
                    return $rootScope.objects[i];
                }
            }
            return null;
        }

        beforeEach(inject(function($injector) {
            $httpBackend = $injector.get('$httpBackend');
            $rootScope = $injector.get('$rootScope');
            $controller = $injector.get('$controller');
            $q = $injector.get('$q');
            API = $injector.get('API');
            routeParams = jasmine.createSpy('routeParams');
            routeParams.oid = 'root';
            modal = jasmine.createSpy('modal');

            // mock for IE-version-checking JQuery call
            // because oh my god wtf karma
            $ = function(blah) {
                return {
                    is: function(selector) {
                        return false;
                    }
                };
            };
            modalObject = {
                result: {then: jasmine.createSpy('modalObject.result.then')}
            };
            modal.open = jasmine.createSpy('modal.open').andReturn(modalObject);
          
            // Initial root folder state
            folder_1 = {id: 'folder1id', name: "folder 1", is_shared: false};
            folder_2 = {id: 'folder2id', name: "folder 2", is_shared: false};
            file = {id: 'fileid', name: "filename", last_modified: 'today'};

            // Spy setup
            spyOn(API.folder, 'getMetadata')
              .andReturn(
                $q.when( 
                  { data : {
                      name:'AeroFS',
                      id: 'root', 
                      path: {folders: []}, 
                      children: { 
                        folders: [folder_1, folder_2], 
                        files: [file]
                      }  
                    },
                  status : 200
                })
              );

            // /shares/{sid}/urls
            spyOn(API, 'getLinks')
              .andReturn($q.when({data : {urls : []}}));
            
            // users/{email}/shares
            spyOn(API.sf, 'list')
              .andReturn($q.when({data : {}}));

            // shares/{id}
            spyOn(API.sf, 'getMetadata')
              .andReturn($q.when({data : {}}));

            // Still requires $httpBackend since certain services (tokenService) 
            // still use $http
            $httpBackend.whenPOST(/\/json_token(\?t=[01]?.?\d*)?/).respond('token');
            $httpBackend.whenPOST('/json_new_token').respond('newtoken');
            
            FileListCtrl = $controller('FileListCtrl', {'$scope': $rootScope, '$routeParams': routeParams, '$modal': modal});
            $rootScope.$digest();

            expect(API.folder.getMetadata).toHaveBeenCalled();
            expect(API.getLinks).toHaveBeenCalled();
            
            $httpBackend.whenPOST(/\/json_token(\?t=[01]?.?\d*)?/).respond('token');
            $httpBackend.whenPOST('/json_new_token').respond('newtoken');
        }));

        beforeEach(function() {
            folder_1_object = get_object_by_id(folder_1.id);
            expect(folder_1_object).not.toBeNull();

            folder_2_object = get_object_by_id(folder_2.id);
            expect(folder_2_object).not.toBeNull();

            file_object = get_object_by_id(file.id);
            expect(file_object).not.toBeNull();
        });

        it("should rename folder when submitRename is called", function () {
            spyOn(API.folder, 'move').andReturn(
              $q.when(
                { data : {
                    id: folder_1.id,
                    name: 'newname'
                  }
                }
            ));

            folder_1_object.newName = 'newname';
            $rootScope.submitRename(folder_1_object);
            $rootScope.$digest();

            expect(API.folder.move).toHaveBeenCalled();
            expect($rootScope.objects[0].name).toBe('newname');
        });

        it("should not rename folder to empty string", function () {
            spyOn(API.folder, 'move');

            folder_1_object.newName = '';
            $rootScope.submitRename(folder_1_object);
            $rootScope.$digest();

            expect(API.folder.move).not.toHaveBeenCalled();
            expect(folder_1_object.name).toBe(folder_1.name);
        });

        it("should not rename folder to old name", function () {
            spyOn(API.folder, 'move');

            folder_1_object.newName = folder_1.name;
            $rootScope.submitRename(folder_1_object);
            $rootScope.$digest();

            expect(API.folder.move).not.toHaveBeenCalled();
            expect(folder_1_object.name).toBe(folder_1.name);
        });

        it("should fail when folder new name conflicts", function () {
            window.showErrorMessage = jasmine.createSpy();
            spyOn(API.folder, 'move').andReturn($q.reject({status : 409}));

            folder_1_object.newName = 'newname';
            $rootScope.submitRename(folder_1_object);
            $rootScope.$digest();

            expect(API.folder.move).toHaveBeenCalled();
            expect(window.showErrorMessage).toHaveBeenCalled();
            expect(folder_1_object.name).toBe(folder_1.name);
        });

        it("should move folder into sibling", function() {
            spyOn(API.folder, 'listChildren').andReturn($q.when(
              { status : 200,
                data : {
                  folders: [folder_1, folder_2], 
                  files: []
                }
              }
            ));

            spyOn(API.folder, 'move').andCallFake(function() {
              return $q.when(
              { status : 200,
                data : folder_1});
            });

            $rootScope.startMove(folder_1_object);
            $rootScope.$digest();

            expect(modal.open).toHaveBeenCalled();
            expect(API.folder.listChildren).toHaveBeenCalled();           

            // move folder_1 into folder_2
            $rootScope.submitMove(folder_1_object, folder_2);
            $rootScope.$digest();

            // verify that the view was updated to remove folder_1
            expect(API.folder.move).toHaveBeenCalledWith(folder_1.id, folder_2.id, folder_1.name);
            expect(get_object_by_id(folder_1.id)).toBeNull();
        });

        it("should move file into sibling", function() {
            spyOn(API.folder, 'listChildren').andReturn($q.when(
              { status : 200,
                data : {
                  folders: [folder_1, folder_2], 
                  files: []
                }
              }
            ));
            spyOn(API.file, 'move').andReturn($q.when(
              { status : 200,
                data : file}
            ));

            $rootScope.startMove(file_object);
            $rootScope.$digest();
            expect(modal.open).toHaveBeenCalled();
            expect(API.folder.listChildren).toHaveBeenCalled();
 
            // move file into folder_1
            $rootScope.submitMove(file_object, folder_1);
            $rootScope.$digest();

            // verify that the view was updated to remove folder_1
            expect(API.file.move).toHaveBeenCalledWith(file.id, folder_1.id, file.name);
            expect(get_object_by_id(file.id)).toBeNull();
        });

        it("should not move folder into itself", function() {
            window.showErrorMessage = jasmine.createSpy();
            spyOn(API.folder, 'listChildren').andReturn($q.when(
              { status : 200,
                data : {
                  folders: [folder_1, folder_2],
                  files: []
                }
              }
            ));
            spyOn(API.folder, 'move');

            $rootScope.startMove(file);
            $rootScope.$digest();

            expect(modal.open).toHaveBeenCalled();
            expect(API.folder.listChildren).toHaveBeenCalled();           

            // move folder_1 into folder_1
            $rootScope.submitMove(folder_1_object, folder_1);
            $rootScope.$digest();

            expect(window.showErrorMessage).toHaveBeenCalled();
            expect(API.folder.move).not.toHaveBeenCalled();
            // verify that the view was not updated to remove folder_1
            expect(get_object_by_id(folder_1.id)).not.toBeNull();
        });

        it("should not move a folder to the same location", function() {
            spyOn(API.folder, 'listChildren').andReturn($q.when(
              { status : 200,
                data :  {
                  folders: [folder_1, folder_2],
                  files: []
                }
              }
            ));
            spyOn(API.folder, 'move');

            $rootScope.startMove(file);
            $rootScope.$digest();
            expect(modal.open).toHaveBeenCalled();

            // move folder_1 into root
            $rootScope.submitMove(folder_1_object, {id: 'root'});
            $rootScope.$digest();
            expect(API.folder.move).not.toHaveBeenCalled();

            // verify that the view was not updated to remove folder_1
            expect(get_object_by_id(folder_1.id)).not.toBeNull();
        });

        it("should prompt user for confirmation when delete button is clicked", function() {
            spyOn(API.folder, 'remove');

            $rootScope.startDelete(folder_1_object);
            $rootScope.$digest();
            expect(modal.open).toHaveBeenCalled();

            expect(API.folder.remove).not.toHaveBeenCalled();
        });

        it("should delete folder when intent to delete is confirmed", function() {
            spyOn(API.folder, 'remove').andReturn($q.when({status : 204}));

            $rootScope.submitDelete(folder_1_object);
            $rootScope.$digest();
            
            expect(API.folder.remove).toHaveBeenCalled();
            expect(get_object_by_id(folder_1.id)).toBeNull();
        });

        it("should delete file when intent to delete is confirmed", function() {
            spyOn(API.file, 'remove').andReturn($q.when({status : 204}));

            $rootScope.submitDelete(file_object);
            $rootScope.$digest();

            expect(API.file.remove).toHaveBeenCalled();
            expect(get_object_by_id(file.id)).toBeNull();
        });

        it("should not remove file from view when deletion fails", function() {
            window.showErrorMessage = jasmine.createSpy();
            spyOn(API.file, 'remove').andReturn($q.reject({status : 404}));

            $rootScope.submitDelete(file_object);
            $rootScope.$digest();

            expect(get_object_by_id(file.id)).not.toBeNull();
            expect(API.file.remove).toHaveBeenCalled();
            expect(window.showErrorMessage).toHaveBeenCalled();
        });
    });
});
