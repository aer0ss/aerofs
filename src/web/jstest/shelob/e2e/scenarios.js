// N.B. these tests depend on some httpBackend mocking which is in appTest.js
// The dependency on this file is accomplished by navigating to index.html, which
// includes appTest.js

describe('Shelob App', function() {
    it('should show contents of root when first loaded', function() {
        browser().navigateTo('/index.html');
        expect(repeater('.file-list-body .object').count()).toBe(3);
    });

    it('should show contents of subfolder when navigated to #/:oid', function() {
        browser().navigateTo('/index.html#/9f89f89f8');
        expect(repeater('.file-list-body .object').count()).toBe(4);
    });

    it('should navigate to subfolder when subfolder link is clicked', function() {
        browser().navigateTo('/index.html');
        element('.file-list-body .object:eq(0) .name a').click();  // empty_folder
        expect(browser().location().url()).toBe('/01a01a01a');
        expect(repeater('.file-list-body .object').count()).toBe(0);
    });

    it('should show folder icon for folder', function() {
        browser().navigateTo('/index.html');
        expect(element('.file-list-body .object:eq(1) .icon img').attr('src'))
            .toEqual('/static/shelob/img/icons/40x40/filetype_folder.png');
    });

    it('should show text file icon for mimetype text/plain', function() {
        browser().navigateTo('/index.html');
        expect(element('.file-list-body .object:eq(2) .icon img').attr('src')) // website.html
            .toEqual('/static/shelob/img/icons/40x40/filetype_text.png');
    });

    it('should show generic file icon for unknown mimetype', function() {
        browser().navigateTo('/index.html#/9f89f89f8');
        expect(element('.file-list-body .object:eq(1) img').attr('src'))  // otherfile
            .toEqual('/static/shelob/img/icons/40x40/filetype_generic.png');
    });

    it('should show "Home" link in breadcrumb trail when page is loaded', function() {
        browser().navigateTo('/index.html');
        expect(element('ul.breadcrumb li').count()).toEqual(1);
        element('ul.breadcrumb li').click();
        expect(browser().location().url()).toBe('/');
    });

    it('should add folders to breadcrumb trail as subfolder links are clicked', function() {
        browser().navigateTo('/index.html');
        expect(element('ul.breadcrumb li').count()).toEqual(1);
        element('.file-list-body .object:eq(1) .name a').click();  // other_folder
        expect(element('ul.breadcrumb li').count()).toEqual(2);
        element('.file-list-body .object:eq(0) .name a').click();  // deeper_folder
        expect(element('ul.breadcrumb li').count()).toEqual(3);
    });

    it('should should remove folders from breadcrumb trail if a breadcrumb link is clicked ', function() {
        browser().navigateTo('/index.html');
        element('.file-list-body .object:eq(1) .name a').click();  // other_folder
        element('.file-list-body .object:eq(0) .name a').click();  // deeper_folder
        expect(element('ul.breadcrumb li').count()).toEqual(3);
        element('ul.breadcrumb li:eq(1) a').click();  // other_folder
        expect(browser().location().url()).toBe('/9f89f89f8');
        expect(element('ul.breadcrumb li').count()).toEqual(2);
    });
});
