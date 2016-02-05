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

        var $httpBackend, $rootScope, $controller, routeParams, modal, FileListCtrl;
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

            FileListCtrl = $controller('FileListCtrl', {'$scope': $rootScope, '$routeParams': routeParams, '$modal': modal});

            $httpBackend.whenPOST(/\/json_token(\?t=[01]?.?\d*)?/).respond('token');
            $httpBackend.whenPOST('/json_new_token').respond('newtoken');
            $httpBackend.whenGET('/api/v1.3/shares/root/urls').respond({ urls: [] });
            $httpBackend.whenGET('/api/v1.2/shares/root').respond({});
            $httpBackend.whenGET('/api/v1.3/users/me/shares').respond({});
        }));

        beforeEach(function() {
            folder_1 = {id: 'folder1id', name: "folder 1", is_shared: false};
            folder_2 = {id: 'folder2id', name: "folder 2", is_shared: false};
            file = {id: 'fileid', name: "filename", last_modified: 'today'};
            $httpBackend.expectGET(/\/api\/v1.2\/folders\/root\/?\?fields=children,path(&.*)?/).respond(200,
                    {name:'AeroFS', id: 'root', path: {folders: []}, children: {folders: [folder_1, folder_2], files: [file]}});
            $httpBackend.flush();

            folder_1_object = get_object_by_id(folder_1.id);
            expect(folder_1_object).not.toBeNull();

            folder_2_object = get_object_by_id(folder_2.id);
            expect(folder_2_object).not.toBeNull();

            file_object = get_object_by_id(file.id);
            expect(file_object).not.toBeNull();
        });

        it("should rename folder when submitRename is called", function () {

            folder_1_object.newName = 'newname';
            $rootScope.submitRename(folder_1_object);

            $httpBackend.expectPUT('/api/v1.2/folders/' + folder_1.id, {parent: 'root', name: 'newname'})
                    .respond(200, {id: folder_1.id, name: 'newname'});
            $httpBackend.flush();

            expect($rootScope.objects[0].name).toBe('newname');
        });

        it("should not rename folder to empty string", function () {
            folder_1_object.newName = '';
            $rootScope.submitRename(folder_1_object);

            $httpBackend.verifyNoOutstandingRequest();

            expect(folder_1_object.name).toBe(folder_1.name);
        });

        it("should not rename folder to old name", function () {
            folder_1_object.newName = folder_1.name;
            $rootScope.submitRename(folder_1_object);

            $httpBackend.verifyNoOutstandingRequest();
            expect(folder_1_object.name).toBe(folder_1.name);
        });

        it("should fail when folder new name conflicts", function () {
            window.showErrorMessage = jasmine.createSpy();

            folder_1_object.newName = 'newname';
            $rootScope.submitRename(folder_1_object);

            // respond as if there were a name conflict
            $httpBackend.expectPUT('/api/v1.2/folders/' + folder_1.id, {parent: 'root', name: 'newname'}).respond(409);
            $httpBackend.flush();

            expect(window.showErrorMessage).toHaveBeenCalled();
            expect(folder_1_object.name).toBe(folder_1.name);
        });

        it("should move folder into sibling", function() {
            $rootScope.startMove(folder_1);
            expect(modal.open).toHaveBeenCalled();
            $httpBackend.expectGET(/\/api\/v1.2\/folders\/root\/children(\?.*)?/).respond(200, {folders: [folder_1, folder_2], files: []});

            // move folder_1 into folder_2
            $rootScope.submitMove(folder_1_object, folder_2);
            $httpBackend.expectPUT('/api/v1.2/folders/' + folder_1.id, {name: folder_1.name, parent: folder_2.id})
                    .respond(200, folder_1);
            $httpBackend.flush();

            // verify that the view was updated to remove folder_1
            expect(get_object_by_id(folder_1.id)).toBeNull();
        });

        it("should move file into sibling", function() {
            $rootScope.startMove(file_object);
            expect(modal.open).toHaveBeenCalled();
            $httpBackend.expectGET(/\/api\/v1.2\/folders\/root\/children(\?.*)?/).respond(200, {folders: [folder_1, folder_2], files: []});

            // move file into folder_1
            $rootScope.submitMove(file_object, folder_1);
            $httpBackend.expectPUT('/api/v1.2/files/' + file.id, {name: file.name, parent: folder_1.id})
                .respond(200, file);
            $httpBackend.flush();

            // verify that the view was updated to remove folder_1
            expect(get_object_by_id(file.id)).toBeNull();
        });

        it("should not move folder into itself", function() {
            window.showErrorMessage = jasmine.createSpy();

            $rootScope.startMove(file);
            expect(modal.open).toHaveBeenCalled();
            $httpBackend.expectGET(/\/api\/v1.2\/folders\/root\/children(\?.*)?/).respond(200, {folders: [folder_1, folder_2], files: []});

            // move folder_1 into folder_1
            $rootScope.submitMove(folder_1_object, folder_1);

            $httpBackend.verifyNoOutstandingRequest();

            expect(window.showErrorMessage).toHaveBeenCalled();

            // verify that the view was not updated to remove folder_1
            expect(get_object_by_id(folder_1.id)).not.toBeNull();
        });

        it("should not move a folder to the same location", function() {
            $rootScope.startMove(file);
            expect(modal.open).toHaveBeenCalled();
            $httpBackend.expectGET(/\/api\/v1.2\/folders\/root\/children(\?.*)?/).respond(200, {folders: [folder_1, folder_2], files: []});

            // move folder_1 into root
            $rootScope.submitMove(folder_1_object, {id: ''});
            $httpBackend.verifyNoOutstandingRequest();

            // verify that the view was not updated to remove folder_1
            expect(get_object_by_id(folder_1.id)).not.toBeNull();
        });

        it("should prompt user for confirmation when delete button is clicked", function() {
            $rootScope.startDelete(folder_1_object);
            expect(modal.open).toHaveBeenCalled();
            $httpBackend.verifyNoOutstandingRequest();
        });

        it("should delete folder when intent to delete is confirmed", function() {
            $rootScope.submitDelete(folder_1_object);
            $httpBackend.expectDELETE('/api/v1.2/folders/' + folder_1.id).respond(204);
            $httpBackend.flush();
            expect(get_object_by_id(folder_1.id)).toBeNull();
        });

        it("should delete file when intent to delete is confirmed", function() {
            $rootScope.submitDelete(file_object);
            $httpBackend.expectDELETE('/api/v1.2/files/' + file.id).respond(204);
            $httpBackend.flush();
            expect(get_object_by_id(file.id)).toBeNull();
        });

        it("should not remove file from view when deletion fails", function() {
            window.showErrorMessage = jasmine.createSpy();
            $rootScope.submitDelete(file_object);
            $httpBackend.expectDELETE('/api/v1.2/files/' + file.id).respond(404);
            $httpBackend.flush();
            expect(get_object_by_id(file.id)).not.toBeNull();
            expect(window.showErrorMessage).toHaveBeenCalled();
        });

        it("should reset the outstanding request count after the API call finishes", function() {
            expect($rootScope.outstandingRequests).toBe(0);
            $rootScope.submitDelete(file_object);
            $httpBackend.expectDELETE('/api/v1.2/files/' + file.id).respond(200);
            $httpBackend.flush();
            expect($rootScope.outstandingRequests).toBe(0);
        });
    });
});
