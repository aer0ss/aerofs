describe('Shelob Controllers', function() {

    beforeEach(module('shelobServices'));
    beforeEach(module('shelobControllers'));

    describe('FileListCtrl', function() {

        var $httpBackend, $rootScope, $controller, routeParams, modal, FileListCtrl;

        beforeEach(inject(function($injector) {
            $httpBackend = $injector.get('$httpBackend');
            $rootScope = $injector.get('$rootScope');
            $controller = $injector.get('$controller');

            routeParams = jasmine.createSpy('routeParams');
            modal = jasmine.createSpy('modal');

            var modalObject = {
                result: {then: jasmine.createSpy('modalObject.result.then')}
            };
            modal.open = jasmine.createSpy('modal.open').andReturn(modalObject);

            FileListCtrl = $controller('FileListCtrl', {'$scope': $rootScope, '$routeParams': routeParams, '$modal': modal});

            $httpBackend.whenGET('/json_token').respond('token');
            $httpBackend.whenGET('/json_new_token').respond('newtoken');
        }));

        it("should rename folder when submitRename is called", function () {
            var folder = {id: 'abc123', name: 'oldname'};
            window.showSuccessMessage = jasmine.createSpy();

            $httpBackend.whenGET(/\/api\/v1.0\/children\//).respond(200, {parent: 'root', folders: [folder], files: []});
            $httpBackend.flush();

            $rootScope.objects[0].newName = 'newname';
            $rootScope.submitRename($rootScope.objects[0]);

            $httpBackend.expectPUT('/api/v1.0/folders/abc123', {parent: 'root', name: 'newname'})
                    .respond(200, {id: 'abc123', name: 'newname'});
            $httpBackend.flush();

            expect(window.showSuccessMessage).toHaveBeenCalled();
            expect($rootScope.objects[0].name).toBe('newname');
        });

        it("should not rename folder to empty string", function () {
            var folder = {id: 'abc123', name: 'oldname'};

            $httpBackend.whenGET(/\/api\/v1.0\/children\//).respond(200, {parent: 'root', folders: [folder], files: []}); $httpBackend.flush();

            $rootScope.objects[0].newName = '';
            $rootScope.submitRename($rootScope.objects[0]);

            $httpBackend.verifyNoOutstandingRequest();

            expect($rootScope.objects[0].name).toBe('oldname');
        });

        it("should not rename folder to old name", function () {
            var folder = {id: 'abc123', name: 'oldname'};

            $httpBackend.whenGET(/\/api\/v1.0\/children\//).respond(200, {parent: 'root', folders: [folder], files: []});
            $httpBackend.flush();

            $rootScope.objects[0].newName = $rootScope.objects[0].name;
            $rootScope.submitRename($rootScope.objects[0]);

            $httpBackend.verifyNoOutstandingRequest();
            expect($rootScope.objects[0].name).toBe('oldname');
        });

        it("should fail when folder new name conflicts", function () {
            var folder = {id: 'abc123', name: 'oldname'};
            window.showErrorMessage = jasmine.createSpy();

            $httpBackend.whenGET(/\/api\/v1.0\/children\//).respond(200, {parent: 'root', folders: [folder], files: []});
            $httpBackend.flush();

            $rootScope.objects[0].newName = 'newname';
            $rootScope.submitRename($rootScope.objects[0]);

            // respond as if there were a name conflict
            $httpBackend.expectPUT('/api/v1.0/folders/abc123', {parent: 'root', name: 'newname'}).respond(409);
            $httpBackend.flush();

            expect(window.showErrorMessage).toHaveBeenCalled();
            expect($rootScope.objects[0].name).toBe('oldname');
        });

        describe("moving things", function() {

            function get_object_by_id(id) {
                for (var i = 0; i < $rootScope.objects.length; i++) {
                    if ($rootScope.objects[i].id == id) {
                        return $rootScope.objects[i];
                    }
                }
                return null;
            }

            var folder_1, folder_2, file, folder_1_object;
            beforeEach(function() {
                folder_1 = {id: 'folder1id', name: "folder 1", is_shared: false};
                folder_2 = {id: 'folder2id', name: "folder 2", is_shared: false};
                file = {id: 'fileid', name: "filename", last_modified: 'today'};
                $httpBackend.expectGET(/\/api\/v1.0\/children\//).respond(200,
                        {parent: 'root', folders: [folder_1, folder_2], files: [file]});
                $httpBackend.flush();

                folder_1_object = get_object_by_id(folder_1.id);
                expect(folder_1_object).not.toBeNull();

                folder_2_object = get_object_by_id(folder_2.id);
                expect(folder_2_object).not.toBeNull();

                file_object = get_object_by_id(file.id);
                expect(file_object).not.toBeNull();
            });

            it("should move folder into sibling", function() {
                window.showSuccessMessage = jasmine.createSpy();

                $rootScope.startMove(folder_1);
                expect(modal.open).toHaveBeenCalled();

                // move folder_1 into folder_2
                $rootScope.submitMove(folder_1_object, folder_2);
                $httpBackend.expectPUT('/api/v1.0/folders/' + folder_1.id, {name: folder_1.name, parent: folder_2.id})
                        .respond(200, folder_1);
                $httpBackend.flush();

                expect(window.showSuccessMessage).toHaveBeenCalled();

                // verify that the view was updated to remove folder_1
                expect(get_object_by_id(folder_1.id)).toBeNull();
            });

            it("should move file into sibling", function() {
                window.showSuccessMessage = jasmine.createSpy();

                $rootScope.startMove(file_object);
                expect(modal.open).toHaveBeenCalled();

                // move file into folder_1
                $rootScope.submitMove(file_object, folder_1);
                $httpBackend.expectPUT('/api/v1.0/files/' + file.id, {name: file.name, parent: folder_1.id})
                    .respond(200, file);
                $httpBackend.flush();

                expect(window.showSuccessMessage).toHaveBeenCalled();

                // verify that the view was updated to remove folder_1
                expect(get_object_by_id(file.id)).toBeNull();
            });

            it("should not move folder into itself", function() {
                window.showErrorMessage = jasmine.createSpy();

                $rootScope.startMove(file);
                expect(modal.open).toHaveBeenCalled();
                // move folder_1 into folder_1
                $rootScope.submitMove(folder_1_object, folder_1);

                $httpBackend.verifyNoOutstandingRequest();
                // verify that the view was not updated to remove folder_1
                folder_1_object = null;

                expect(window.showErrorMessage).toHaveBeenCalled();

                expect(get_object_by_id(folder_1.id)).not.toBeNull();
            });

            it("should not move a folder to the same location", function() {
                $rootScope.startMove(file);
                expect(modal.open).toHaveBeenCalled();

                // move folder_1 into root
                $rootScope.submitMove(folder_1_object, {id: ''});
                $httpBackend.verifyNoOutstandingRequest();

                // verify that the view was not updated to remove folder_1
                expect(get_object_by_id(folder_1.id)).not.toBeNull();
            })
        });
    });
});
