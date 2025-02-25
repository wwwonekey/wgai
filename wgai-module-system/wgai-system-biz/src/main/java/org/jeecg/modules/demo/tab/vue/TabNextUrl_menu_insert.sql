-- 注意：该页面对应的前台目录为views/tab文件夹下
-- 如果你想更改到其他目录，请修改sql中component字段对应的值


INSERT INTO sys_permission(id, parent_id, name, url, component, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_route, is_leaf, keep_alive, hidden, hide_tab, description, status, del_flag, rule_flag, create_by, create_time, update_by, update_time, internal_or_external) 
VALUES ('2025022509511780410', NULL, '模型下发列表', '/tab/tabNextUrlList', 'tab/TabNextUrlList', NULL, NULL, 0, NULL, '1', 0.00, 0, NULL, 1, 1, 0, 0, 0, NULL, '1', 0, 0, 'admin', '2025-02-25 09:51:41', NULL, NULL, 0);

-- 权限控制sql
-- 新增
INSERT INTO sys_permission(id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
VALUES ('2025022509511780411', '2025022509511780410', '添加模型下发列表', NULL, NULL, 0, NULL, NULL, 2, 'org.jeecg.modules.demo:tab_next_url:add', '1', NULL, 0, NULL, 1, 0, 0, 0, NULL, 'admin', '2025-02-25 09:51:41', NULL, NULL, 0, 0, '1', 0);
-- 编辑
INSERT INTO sys_permission(id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
VALUES ('2025022509511780412', '2025022509511780410', '编辑模型下发列表', NULL, NULL, 0, NULL, NULL, 2, 'org.jeecg.modules.demo:tab_next_url:edit', '1', NULL, 0, NULL, 1, 0, 0, 0, NULL, 'admin', '2025-02-25 09:51:41', NULL, NULL, 0, 0, '1', 0);
-- 删除
INSERT INTO sys_permission(id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
VALUES ('2025022509511780413', '2025022509511780410', '删除模型下发列表', NULL, NULL, 0, NULL, NULL, 2, 'org.jeecg.modules.demo:tab_next_url:delete', '1', NULL, 0, NULL, 1, 0, 0, 0, NULL, 'admin', '2025-02-25 09:51:41', NULL, NULL, 0, 0, '1', 0);
-- 批量删除
INSERT INTO sys_permission(id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
VALUES ('2025022509511780414', '2025022509511780410', '批量删除模型下发列表', NULL, NULL, 0, NULL, NULL, 2, 'org.jeecg.modules.demo:tab_next_url:deleteBatch', '1', NULL, 0, NULL, 1, 0, 0, 0, NULL, 'admin', '2025-02-25 09:51:41', NULL, NULL, 0, 0, '1', 0);
-- 导出excel
INSERT INTO sys_permission(id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
VALUES ('2025022509511780415', '2025022509511780410', '导出excel_模型下发列表', NULL, NULL, 0, NULL, NULL, 2, 'org.jeecg.modules.demo:tab_next_url:exportXls', '1', NULL, 0, NULL, 1, 0, 0, 0, NULL, 'admin', '2025-02-25 09:51:41', NULL, NULL, 0, 0, '1', 0);
-- 导入excel
INSERT INTO sys_permission(id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
VALUES ('2025022509511780416', '2025022509511780410', '导入excel_模型下发列表', NULL, NULL, 0, NULL, NULL, 2, 'org.jeecg.modules.demo:tab_next_url:importExcel', '1', NULL, 0, NULL, 1, 0, 0, 0, NULL, 'admin', '2025-02-25 09:51:41', NULL, NULL, 0, 0, '1', 0);