# Network sleep tests

The network sleep tests are meant to check if AeroFS syncs files properly on a machine (OSX and Windows) after it has woken up from sleep. For the purpose of these tests we have two actors:

* Awake actor: This actor never sleeps and its job is to create files in a test directory under its AeroFS folder. The awake actor is a linux machine.
* Sleepy actor: This actor toggles between being asleep and awake. It sleeps while the awake actor is creating files and after file creation is done, comes back up and checks if it is able to sync all the files created in the awake actor while it was asleep. The sleepy actor could either be a OSX or Windows machine.

######Note: Both awake and sleepy actor are signed into the same AeroFS account.

####Procedure
1. First make sure that the sleep actor is awake.
2. Create a test directory in the awake actor.
3. Make the sleepy actor go to sleep.
4. Create a file under the test directory in the awake actor.
5. Bring the sleepy actor come to its senses (meaning wake it). We use wakeonlan (brew install wakeonlan) for this.
6. Check if AeroFS/test directory in the sleepy actor has the same number of files as there are in the AeroFS/test directory in the awake actor.
7. Repeat steps 3 - 6 till there is mismatch in the file count.