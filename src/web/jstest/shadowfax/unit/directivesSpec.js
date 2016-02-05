describe('Shadowfax Directives', function () {

    beforeEach(module('shadowfaxDirectives'));
    beforeEach(module('shelobServices'));
    beforeEach(module('ui.bootstrap'));
    beforeEach(module('typeahead'));
    beforeEach(module('templates'));

    describe('aeroShareFolder', function () {
        var $compile,
            $rootScope,
            $httpBackend,
            $modal,
            element;

        beforeEach(inject(function ($injector) {
            $compile = $injector.get('$compile');
            $rootScope = $injector.get('$rootScope');
            $httpBackend = $injector.get('$httpBackend');
            $modal = $injector.get('$modal');

            //Populate from stubs
            $rootScope.getUsersAndGroupsURL = getUsersAndGroupsURL;

            spyOn($modal, 'open').andCallThrough();
        }));

        describe('initialize', function () {

            beforeEach(function () {
                $rootScope.folder = {
                    "people": [{
                        "can_edit": true,
                        "first_name": "Tina",
                        "last_name": "Turner",
                        "is_owner": true,
                        "is_group": false,
                        "email": "tina.turner@aerofs.com",
                        "is_pending": false
                    },
                    {
                        "can_edit": true,
                        "first_name": "Abigailship",
                        "last_name": "Lasto",
                        "is_owner": false,
                        "is_group": false,
                        "email": "abigailship@127.0.0.1",
                        "is_pending": true
                    }],
                    "name": "another folder",
                    "sid": "857c56eb92810c139323798cc1d0b8e8",
                    "id": "ec64248ee34f34353100d36fd0291855857c56eb92810c139323798cc1d0b8e8",
                    "is_privileged": true,
                    "is_shared": true,
                    "type": "folder",
                };

                $rootScope.me = "tina.turner@aerofs.com"

                element = angular.element('<aero-shared-folder-manager folder="folder" me="me"></aero-shared-folder-manager>');
                $compile(element)($rootScope);
                $rootScope.$digest();
            });

            it('has correct structure', function () {
                expect(element[0].querySelector('a span.glyphicon').textContent).toEqual('');
                expect(element[0].querySelector('a span:nth-child(2)').textContent).toEqual('Manage Sharing');
                expect(element[0].querySelector('a span.ng-hide').textContent).toEqual('View Members');
            });

            it('can open modal', function () {
                var scope = element.isolateScope()
                scope.openModal();
                expect($modal.open).toHaveBeenCalled();
            });
        });
    });
});