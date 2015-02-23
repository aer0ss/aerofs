"""
The situation I really want to reproduce:

  init state:
  n1o1
  n2o2
                 o1->o2
                <-------

How to get there (on d2)

d0          d1                 d2                  d3
--------- ------------ ------------------ ---------------------
 +instance_uniq_path
    (and share it with all devices first)
+no2     | +no1       |                  |
         |          --------------------------->
         |            |                         no1
         |            |                  |
    ------------------------->           |
         |                   no2         |   rename no1 to n2o1
         |            |                  |
         |            |            <------------->
         |            |      no2      exchange     no2
         |            |      n2o1                  n2o1
         |            |                  |
         |            |                  |
    <--------->       |                  |
      exchange        |                  |
  no2        no2      |                  |
 o1->o2     o1->o2    |                  |
         |            |                  |
         |            |                  |
         |      ----------->             |
         |          o1->o2               |

 final state:
    I can't remember... but hopefully there is only one

N.B. don't forget
 * to use lib.files.alias.create_alias_dir to ensure d1
    creates the alias object and d0 creates the target
    (e.g. see should_merge_grandparent_into_subdir_when_gp_is_alias_of_subdir)
 * that no2 must keep its name consistent so that there are no meta conflicts
    about renaming o2.
 * to make a default "observer" device that sits out the syncing until the
   final state has been achieved (e.g. see the grandparent test mentioned
   above)
"""