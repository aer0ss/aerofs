AeroFS Wiki
===

# Problem

Sales and Marketing are not able to read engineering and product content. We should have a better way to browse and search information. 


# Solution

Using the open-source [gollum](https://github.com/gollum/gollum) from GitHub.

A proof-of-concept of Gollum has been implemented at [wiki.arrowfs.org](wiki.arrowfs.org:8000).

### Things to do

- push and pull from gerrit
- handle conflicts
- authentication
- polish


# Challenges 

- AeroFS Wiki needs to push on gerrit without review process.
- There will be many new commits as non-technical start using the software. This might makes the existing code repo notification channels very noisy (email, Slack). 