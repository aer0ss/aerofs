describe('Shelob Directives', function () {

    beforeEach(module('shelobDirectives'));
    beforeEach(module('ui.bootstrap'));
    beforeEach(module('shelobFilters'));
    beforeEach(module('templates'));

    describe('aeroLinkOptions', function () {
        var $compile,
            $rootScope,
            $httpBackend,
            element;

        beforeEach(inject(function ($injector) {
            $compile = $injector.get('$compile');
            $rootScope = $injector.get('$rootScope');
            $httpBackend = $injector.get('$httpBackend');

            //Populate from stubs
            $rootScope.removeLink = removeLink;
        }));

        describe('initialize', function () {

            beforeEach(function () {
                $rootScope.link = {
                    expires: 0,
                    has_password: false,
                    require_login: false
                };

                element = angular.element('\
                    <div aero-link-options \
                    ng-model="link" \
                    title="Options"\
                    on-delete="removeLink(object, key)"></div>');

                $compile(element)($rootScope);
                $rootScope.$digest();
            });

            it('has correct structure', function (){
                expect(element.find(".btn-group")).toBeDefined;
                expect(element.find("button.btn.btn-default").text()).toContain('Options');
                expect(element.find(".dropdown-menu li").length).toEqual(7);
            });

            it('has correct defaults', function (){
                expect(element.find(".dropdown-menu li:nth(0)").text()).toContain("Add Expiration Time");
                expect(element.find(".dropdown-menu li:nth(1)").text()).toContain('Expires: Never');
                expect(element.find(".dropdown-menu li:nth(2)").text()).toContain('Add Password');
                expect(element.find(".dropdown-menu li:nth(3)").text()).toContain('Remove Password');
                expect(element.find(".dropdown-menu li:nth(4)").text()).toContain('Require Sign In');
                expect(element.find(".dropdown-menu li:nth(6)").text()).toContain('Delete Link');
            });
        });

        describe('setRequireLogin', function () {

            beforeEach(function () {
                $rootScope.link = {
                    expires: 0,
                    has_password: false,
                    require_login: false,
                    key: 'test'
                };

                element = angular.element('\
                    <div aero-link-options \
                    ng-model="link" \
                    title="Options"\
                    on-delete="removeLink(object, key)"></div>');

                $compile(element)($rootScope);
                $rootScope.$digest();
            });

            beforeEach(function () {
                $httpBackend
                    .whenPOST("/set_url_require_login")
                    .respond(200, {});
            });

            it('sends a post to server to toggle set login', function () {
                var scope = element.isolateScope();

                //False -> True
                spyOn(window, 'showSuccessMessage').andCallFake(function (msg) { return false} );
                expect(scope.link.require_login).toEqual(false);
                scope.setRequireLogin();

                $httpBackend.expectPOST("/set_url_require_login", {
                    key: $rootScope.link.key,
                    require_login: true
                }).respond(200, {});
                $httpBackend.flush();

                expect(window.showSuccessMessage.argsForCall[0][0]).toEqual('Only signed-in users can access the link.');
                expect(scope.link.require_login).toEqual(true);

                //True -> False
                scope.setRequireLogin();

                $httpBackend.expectPOST("/set_url_require_login", {
                    key: $rootScope.link.key,
                    require_login: false
                }).respond(200, {});
                $httpBackend.flush();

                expect(window.showSuccessMessage.argsForCall[1][0]).toEqual('Anyone with the link can access it.');
                expect(scope.link.require_login).toEqual(false);

            })

        });

    });

});