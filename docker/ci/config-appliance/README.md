### Why Firefox not Chromium?

We use Firefox as the driver. The reasons are:

- Setting up headless Chrome is 
[way trickier](http://stackoverflow.com/a/15514348) than 
[headless Firefox](http://www.semicomplete.com/blog/geekery/xvfb-firefox.html).

- Chrome requires privileged containers with is less great.
 
- Most devs use Chrome for development, so we cover Firefox in testing.

### References

Selenium WebDriver Python binding: http://selenium-python.readthedocs.org/en/latest/