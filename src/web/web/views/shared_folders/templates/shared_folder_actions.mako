<div class="sf-actions btn-group">
  <button type="button" class="btn btn-default dropdown-toggle" data-toggle="dropdown">
    Actions <span class="caret"></span>
  </button>
  <ul class="dropdown-menu" role="menu">
    <li>
      <a href="#" class="${open_modal_class}" data-${data_sid}="${sid}" data-${data_privileged}="${is_privileged}" data-${data_name}="${folder_name}" data-${data_perms}="${perms}" data-action="manage">
        <span class="glyphicon glyphicon-user"></span> 
        % if is_privileged:
          Manage Members
        % else:
          View Members
        % endif
      </a>
    </li>
    <li>
      <a href="#" class="${open_modal_class}" data-${data_sid}="${sid}" data-${data_name}="${folder_name}" data-action="leave">
        <span class="icon">
          <img src="${request.static_path('web:static/img/icons/exit.svg')}" onerror="this.onerror=null; this.src='${request.static_path('web:static/img/icons/exit.png')}'"/>
        </span>
          Leave
      </a>
    </li>
  </ul>
</div>