# Two factor auth, mandatory enforcement

Two factor authentication was a great success and now customers want to be able to
require it for all their employee accounts.  This document breaks down the design
and implementation of two-factor auth mandatory enforcement and lists tasks.

### Technical plan

Three state enum for org-level two factor enforcement, stored in SPDB as
`sp_organization.o_tfa_level`:

  - `DISALLOWED`
  - `OPT_IN` (default)
  - `MANDATORY`

New exception: ExSecondFactorSetupRequired.
Thrown for any privileged request if session has `BASIC` provenance but not
`BASIC_PLUS_TWO_FACTOR`, `org.o_tfa_level` == `MANDATORY` and
`u_two_factor_enforced` == FALSE.  Used to railroad the user into setting up
their second factor.

New field in SignInUserReply and OpenIdSessionAttributes:
  - `optional bool needSecondFactorSetup`
  This is true iff org requires two factor and user's enforcement is false.
  It serves as a hint to both web and gui that next action should be 2FA setup,
  rather than attempting to perform other actions or provide proof of second factor.

Login process for user if 2FA `DISALLOWED`:

  - User submits basic auth (username/password, or openid, or whatever)
  - User is logged in, session can do anything.

Login process for user if 2FA `OPT_IN`:

  - User submits basic auth (username/password, or openid, or whatever)
  - If `user.u_two_factor_enforced == FALSE`
    - User has a valid session.  All done!
  - If `user.u_two_factor_enforced == TRUE`
    - User is prompted for second factor
    - User submits second factor (or backup code)
    - User is logged in, session can do anything.

Login process for user if 2FA `MANDATORY`:

  - User submits basic auth (username/password, or openid, or whatever)
  - If `user.u_two_factor_enforced == TRUE`
    - User is prompted for second factor
    - User submits second factor (or backup code)
    - User is logged in, session can do anything.
  - If `user.u_two_factor_enforced == FALSE`:
    - User's account needs two factor to be set up.  User may ONLY go through the
      2FA setup flow - all other access is forbidden.
    - User gets new secret, QR code.
    - User submits proof of secret import.
    - `user.u_two_factor_enforced` is set to TRUE, User is logged in.


# Tasks

### Mandatory

* SP and SPDB changes
  * Schema change to add `o_tfa_level` with sane default value
  * DB wrappers to read/write `o_tfa_level`
  * method on Organization to access `o_tfa_level` enum
  * Add RPC to read org TFA level for display in web (might be needed for disabling non-admin 2FA setup link)
  * Add RPC to set org TFA level to a new value
  * modify getAuthenticatedUserLegacyProvenance to check `o_tfa_level`
    * skip 2FA check if `DISALLOWED`
    * keep current behavior if `OPT_IN`
    * raise ExSecondFactorSetupRequired if `MANDATORY` and `u_two_factor_enforced` is FALSE
    * raise ExSecondFactorRequired if `MANDATORY` and `u_two_factor_enforced` is TRUE but session lacks `BASIC_PLUS_TWO_FACTOR` provenance
  * Add RPC to destroy SP sessions for all users in my organization (org admin only)
  * Add RPC to destroy SP sessions for a specific user (self or org admin)
  * Add RPC to destroy SP sessions for all users (site-admin only? AeroFS Inc. only?)
  * Auditing:
    * On `o_tfa_level` change
    * On user TFA enforcement change
      * Including on admin disable
  * unit tests for all these things
* User flows in web:
  * org admin view for modifying two-factor enforcement state (radio buttons with descriptions of the options)
  * hide second factor setup in Settings if disabled for org
  * explanation page for new users who need to set up 2FA, before they enter
    the 2FA enrollment flow
    * explanation copy for "why you have to set up 2FA"
  * plumbing to handle ExSecondFactorSetupRequired and redirect to explanation page
* User flows in desktop:
  * Team server: just add another page to the wizard, and go to it if
    `needSecondFactorSetup` is true
  * Client: substantially harder, since the existing UI there is really minimal
    and awkwardly structured.  It might be worth moving the regular client to
    also use a wizard-like setup flow.
  * CLI: print message explaining 2FA requirement and URL for setting up 2FA, abort login.


### Optional, but relevant

* Bunker view for expiring all user sessions
* web org admin view for destroying user sessions for my own org
* web org admin view for destroying user sessions for a particular user in my org
* Sparta changes
  * Add org-level route for reading org TFA level? (Is this even useful?)
