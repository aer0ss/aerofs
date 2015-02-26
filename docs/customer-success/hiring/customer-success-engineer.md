# Customer Success Engineer

## Responsibilities

The customer success engineer (CSE) will be reponsible for:

* The technical side of <u>customer success management</u>, which is composed
  of:
    * [Implementation](../implementation.html)
        * AKA "sales engineering".
        * Helping scale and deploy the AeroFS appliance and other AeroFS
          products and systems, and more generally serving as the
          technical point of contact for our customers).
    * [Support](../support.html)
        * Responding to customer questions and diagnosing bug reports.
        * Fixing bugs in our software.
    * [Solutions](../solutions.html)
        * AKA "professinal services".
        * Designing and implementing customer-specific solutions.

The ideal candidate is an mixture of software engineer, product manager, sales
person, and customer support person. This multi-faceted role requires an
extremely flexible individual.

While this position involves some "dirty work" (e.g. working with customers
that have little to no computer experience), it also involves some incredibly
difficult work (e.g. deploying our service at large finalcial institutions).
Therefore I (Matt) would describe this position as a "senior practitioner"
type role.

## Minumum Requirements

Publicly we are asking for the following minimum requirements:

* 2+ years experience in a customer facing, technical role.
* B.S. in Computer Science, Computer Engineering, or equivalent work
  experience.
* Strong knowledge of at least one programming language (Shell scripting is a
  great bonus).
* Familiarity with the Unix working environment.

And the following bonus points:

* Experience in B2B software
* Experience in enterprise software

The first two points (as well as general communication skills, cultural fit,
work ethic, and a high level of the remaining items) are evaluated during the
"fit" interview (currently conducted by Matt). The latter two of the minimum
requirements are evaluated in more depth during the technical phone screen.

Of course, all of the above points + general intelligence + analytical ability
are evaluated during the on-site as well (if the candidate gets to that stage).

To summarize, at a high level we are looking for:

* Strong communication skills
* Cultural fit (and work ethic)
* Technical skills (baseline programming, unix) and intelligence/analytical
  ability
* Experience (customer facing, B2B)

Canditates will be evaluated in all of these areas.

## Technical Phone Screen Questions

The purpose of the technical phone screen is to evaluate basic, <u>baseline</u>
proficiency with above points 3 and 4, with an eye on debugging skills and
systems knowledge (as opposed to algorithms and design -- desirable traits
for development engineers but not necessarily CSEs).

The following are example questions. You can use them, or your own variants of
them if you prefer.

### 1. Basic Coding

* The candidate has to write some simple code, with correct syntax, in any
  language they choose.
* It should be a trivial problem with very little to no algorithmic emphasis,
  where even a slow candidate can answer in <5 minutes.
* Use <a href="http://stypi.com">stypi.com</a> for code sharing.
* Examples:
    * Reverse a string.
    * Write a function to compute the Nth fibonacci number (provide the
      definition of fibonacci numbers if needed).

### 2. Debugging

* We are going to provide the candidate with broken code. They are going to
  try to fix it.
* What we are looking for in this section is a systematic way of approaching
  a debugging problem. They should be asking questions like:
    * What is the code supposed to do overall?
    * What is it doing?
    * What does each line do?
    * If I gave the code this specific input, what would it do?
* We are <b>not</b> as interested in syntax here. We are mostly concerned with
  thought process. Because they might not know the language, offer help with
  respect to syntax whereever possible. You might need to explain concepts like
  memory management, etc., and that is okay. As much as we are looking for a
  solution here, we are also looking at the way this person collaborates with
  you, the engineer.

Given:

    class Node
    {
    public:
        Node *next;
    };

    class LinkedList
    {
    public:
        Node *head;
        void removeHead();
    };

    void LinkedList::removeHead()
    {
        free(head);
        head = head -> next;
    }

Possible solution:

    void LinkedList::removeHead()
    {
        if (head == NULL) return;
        Node *newHead = head -> next;
        free(head);
        head = newHead;
    }

### 3. Unix Proficiency

* Evaluate basic command line proficiency.
    1. How do you list the contents of a directory? (`ls`)
    2. How do you move to a different folder on the file system? (`cd`)
    3. How do you find a file with a specific name? (`find`)
    4. How do you find the current directory? (`pwd`)
    5. How do you check if a file is execuatable? (`ls -l`)
    6. How do you make a file executable? (`chmod +x`)
    7. How do you update the last modified time on a file? (`touch`)
    8. What are hard links and what are soft links? How do you create them?
       (`ln` and `ln -s` respectively).
    9. How do you check if a filesystem is full? (`df`).
   10. How do you mount a block device? (`mount`)

### 4. Scripting and Regular Expressions

* Many engineers would happily spend a week writing a 2,500-line program to do
  something you can do in ~30 seconds with a simple Unix command.
* The candidate should have a reasonable clue, but does not have to give exact
  syntax.
* Even something as simple as "Umm...I would use grep" is ok, as long as they
  can tell you where they would find the syntax.
* Example question: Find all user emails of users who are affected by a certain
  assertion error or exception.

### Final Notes

If the candidate hits it out of the park and you finish early -- great. Take
some time to answer any of their questions and sell them on AeroFS. For now,
please do not cut interviews short. A comprehensive evaluation of candidates
is requested. Comprehensive reviews are important because, in some cases, we
might proceed with a candidate even if they lack skills in a particular area.
This lack of experience can be balanced out with other teams members that have
complementary skills.
