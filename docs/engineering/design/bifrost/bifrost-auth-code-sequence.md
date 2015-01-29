The following can be pasted into www.websequencediagrams.com
---


    App->User: Prompt user to GET consent page
    User->Pyramid: GET consent page (client_id, scope, redirect_uri, state)
    Pyramid->+Bifrost: GET /client/client_id
    Bifrost->-Pyramid: client info
    note right of Pyramid: Pyramid verifies that the client info is correct
    Pyramid->+SP: Request proof-of-identity nonce
    SP->-Pyramid: nonce nonce revolution
    Pyramid->User: Consent page (w/ nonce)
    User->Bifrost: I authorize (with proof of identity) "app" to do "scope" (POST to /auth/authorize)
    Bifrost->+SP: Check proof of identity
    SP->-Bifrost: Proof is valid
    note right of Bifrost: Bifrost generates code tied to (user, client, scope)
    Bifrost->User: 302 to redirect_uri with code
    User->App: GET redirect_uri with code
    App->+Bifrost: POST /token (code, client creds)
    Bifrost->-App: token, scope, expiry

