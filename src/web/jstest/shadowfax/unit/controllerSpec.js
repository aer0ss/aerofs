describe('Shadowfax Controllers', function () {

    beforeEach(module('shadowfaxControllers'));


    describe('SharedFoldersController', function () {

        var $controller,
            $rootScope,
            $httpBackend,
            createController,
            sharedFoldersData;

        beforeEach(inject(function ($injector) {
            $rootScope = $injector.get('$rootScope');
            $controller = $injector.get('$controller');
            $httpBackend = $injector.get('$httpBackend');

            //TODO: pull out into helper file or something (MB)
            modalObject = {
                result: {then: jasmine.createSpy('modalObject.result.then')}
            };
            modal = jasmine.createSpy('modal');
            modal.open = jasmine.createSpy('modal.open').andReturn(modalObject);

            createController = function () {
                controller = $controller('SharedFoldersController', {
                    $scope: $rootScope,
                    $rootscope: $rootScope,
                    $modal: modal
                });
                $httpBackend.flush(); //Execute http requests that happen on instantiation
                return controller
            }
            //TODO: Jasmine fixtures!
            sharedFoldersData = {
                data: [{
                    "groups": [],
                    "owners": [{
                        "can_edit": true,
                        "first_name": "Tina",
                        "last_name": "Turner",
                        "is_owner": true,
                        "is_group": false,
                        "email": "tina.turner@aerofs.com",
                        "is_pending": false
                        }
                    ],
                    "name": "another folder",
                    "members": [{
                        "can_edit": true,
                        "first_name": "Abigailship",
                        "last_name": "Lasto",
                        "is_owner": false,
                        "is_group": false,
                        "email": "abigailship@aerofs.com",
                        "is_pending": true
                    }],
                    "sid": "857c56eb92810c139323798cc1d0b8e8",
                    "is_member": true,
                    "is_left": false,
                    "is_privileged": 1
                }],
                total: 3,
                offset: 0,
                me: {}
            };

            $httpBackend
                .whenGET(dataUrl)
                .respond(sharedFoldersData);
            $httpBackend
                .expectGET(dataUrl + '?offset=0')
                .respond(sharedFoldersData);

            //By putting this in the beforeEach, it will run before each it
            //statement. The rootscope in each test refers to the scope
            //given to each controller
            createController();
        }));

        afterEach(function() {
            $httpBackend.verifyNoOutstandingExpectation();
            $httpBackend.verifyNoOutstandingRequest();
        });

        //Not an exposed method, consider this BDD
        describe('getData', function () {

            it('should get the folders and set up scope', function () {
                expect($rootScope.substring).toBeUndefined;
                expect($rootScope.folders.length).toBe(sharedFoldersData.data.length);
                expect($rootScope.paginationInfo.total).toBe(sharedFoldersData.total);
                expect($rootScope.paginationInfo.limit).toBe(paginationLimit);
            });

            it('should should set the cache', function () {
                expect($rootScope.initialLoad.folders.length).toBe(sharedFoldersData.data.length);
                expect($rootScope.initialLoad.total).toBe(sharedFoldersData.total);
            });

        });

        describe('$scope.updateFolders', function() {

            it('should update internal scope values', function () {
                $rootScope.updateFolders(sharedFoldersData.data.slice(0,1), 1, 'foo');
                expect($rootScope.folders.length).toBe(1);
                expect($rootScope.paginationInfo.total).toBe(1);
                expect($rootScope.substring).toBe('foo');
            });

        });

        describe('$scope.restore', function () {

            it('should update internal scope values from cache if cache', function () {
                $rootScope.substring = 'foo';
                $rootScope.paginationInfo.offset = 5;
                $rootScope.folders = [];
                //Expect it to reload from cache
                $rootScope.restore();
                expect($rootScope.folders.length).toBe($rootScope.initialLoad.folders.length);
                expect($rootScope.paginationInfo.total).toBe($rootScope.initialLoad.total);
                expect($rootScope.substring).toBe('');
                expect($rootScope.paginationInfo.offset).toBe(0);
            });

            it('should update internal scope values from request if no cache', function () {
                $rootScope.substring = 'foo';
                $rootScope.paginationInfo.offset = 5;
                $rootScope.folders = [];
                $rootScope.initialLoad = {};
                //Expect it to reload from http get to list folders
                $rootScope.restore();
                $httpBackend.expectGET(dataUrl + '?offset=0').respond(sharedFoldersData);
                $httpBackend.flush();
                expect($rootScope.folders.length).toBe($rootScope.initialLoad.folders.length);
                expect($rootScope.paginationInfo.total).toBe($rootScope.initialLoad.total);
                expect($rootScope.substring).toBe('');
                expect($rootScope.paginationInfo.offset).toBe(0);
            });
        });

        describe('$scope.paginationInfo.callback', function () {

            it('should reset offset and substring', function () {
                var offset = 3;
                var substring = 'bar';
                $rootScope.paginationInfo.callback(offset, substring);
                expect($rootScope.paginationInfo.offset).toBe(offset);
                expect($rootScope.substring).toBe(substring);

                $httpBackend.expectGET(dataUrl + '?offset=' + offset + '&substring=' + substring).respond(sharedFoldersData);
                $httpBackend.flush()
            });

            it('should reset offset', function () {
                var offset = 3;
                $rootScope.paginationInfo.callback(offset);
                expect($rootScope.paginationInfo.offset).toBe(offset);
                expect($rootScope.substring).toBe('');

                $httpBackend.expectGET(dataUrl + '?offset=' + offset).respond(sharedFoldersData);
                $httpBackend.flush()
            });
        });
    });
});