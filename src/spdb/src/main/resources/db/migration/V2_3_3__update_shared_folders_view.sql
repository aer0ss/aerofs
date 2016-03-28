-- Create a view that will make searching on shared folders by folder names easier
-- Because a user may or may not have renamed a file, it is easier to create a view
-- of shared folders, users, and the file name(with respect to the user) for efficient
-- querying later. This view does not include root stores (see 'where' clause). Mainly typeahead related.
-- The update contains code to include the folder state

create or replace view sp_shared_folders_view as
select
    a_sid as sfv_sid,
    a_id as sfv_user_id,
    a_state as sfv_state,
    a_role as sfv_role,
    case
        when sn_name is not null and a_id = sn_user_id
        then sn_name
        else sf_public_name
        end as sfv_name
from sp_acl
inner join sp_shared_folder on a_sid = sf_id
left join sp_shared_folder_names on sf_id = sn_sid and a_id = sn_user_id
where substr(hex(a_sid),13,1)='0';