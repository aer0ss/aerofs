console.group("Hello! It looks like you're taking a peek behind the curtain on the AeroFS website. Neat!");
console.warn("If you've found a bug, please let us know: https://support.aerofs.com/hc/en-us/articles/201440860");
console.info("Or if you'd like to help us make this website better, type jobs.apply().");
console.groupEnd();

jobs = function(human, heart) {
    if (typeof(human) === 'string') {
        console.log("AeroFS wants YOU, " + human + "! Hope you apply soon. :)");
    }
    if (heart && this === jobs.this) {
        console.log("<3");
    }
    if (this != jobs.this) {
        console.log("<3 <3 <3");
    }
    return 'https://www.aerofs.com/careers/';
};

jobs.this = this;