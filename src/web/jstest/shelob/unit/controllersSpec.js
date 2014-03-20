describe('Shelob Controllers', function() {

    beforeEach(module('shelobServices'));
    beforeEach(module('shelobControllers'));

    describe('FileListCtrl', function() {

        var $httpBackend, $rootScope, $controller, routeParams, FileListCtrl;

        beforeEach(inject(function($injector) {
            $httpBackend = $injector.get('$httpBackend');
            $rootScope = $injector.get('$rootScope');
            $controller = $injector.get('$controller');

            routeParams = jasmine.createSpy('routeParams');

            FileListCtrl = $controller('FileListCtrl', {'$scope': $rootScope, '$routeParams': routeParams});

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

            $httpBackend.whenGET(/\/api\/v1.0\/children\//).respond(200, {parent: 'root', folders: [folder], files: []});
            $httpBackend.flush();

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
    });
});
