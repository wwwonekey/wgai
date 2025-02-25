-- 注意：该页面对应的前台目录为views/video文件夹下
-- 如果你想更改到其他目录，请修改sql中component字段对应的值


INSERT INTO sys_permission(id, parent_id, name, url, component, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_route, is_leaf, keep_alive, hidden, hide_tab, description, status, del_flag, rule_flag, create_by, create_time, update_by, update_time, internal_or_external) 
VALUES ('2025022509019230190', NULL, '采集图片配置', '/video/tabAiClickpicSettingList', 'video/TabAiClickpicSettingList', NULL, NULL, 0, NULL, '1', 0.00, 0, NULL, 1, 1, 0, 0, 0, NULL, '1', 0, 0, 'admin', '2025-02-25 09:01:19', NULL, NULL, 0);

-- 权限控制sql
-- 新增
INSERT INTO sys_permission(id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
VALUES ('2025022509019230191', '2025022509019230190', '添加采集图片配置', NULL, NULL, 0, NULL, NULL, 2, 'org.jeecg.modules.demo:tab_ai_clickpic_setting:add', '1', NULL, 0, NULL, 1, 0, 0, 0, NULL, 'admin', '2025-02-25 09:01:19', NULL, NULL, 0, 0, '1', 0);
-- 编辑
INSERT INTO sys_permission(id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
VALUES ('2025022509019230192', '2025022509019230190', '编辑采集图片配置', NULL, NULL, 0, NULL, NULL, 2, 'org.jeecg.modules.demo:tab_ai_clickpic_setting:edit', '1', NULL, 0, NULL, 1, 0, 0, 0, NULL, 'admin', '2025-02-25 09:01:19', NULL, NULL, 0, 0, '1', 0);
-- 删除
INSERT INTO sys_permission(id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
VALUES ('2025022509019230193', '2025022509019230190', '删除采集图片配置', NULL, NULL, 0, NULL, NULL, 2, 'org.jeecg.modules.demo:tab_ai_clickpic_setting:delete', '1', NULL, 0, NULL, 1, 0, 0, 0, NULL, 'admin', '2025-02-25 09:01:19', NULL, NULL, 0, 0, '1', 0);
-- 批量删除
INSERT INTO sys_permission(id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
VALUES ('2025022509019230194', '2025022509019230190', '批量删除采集图片配置', NULL, NULL, 0, NULL, NULL, 2, 'org.jeecg.modules.demo:tab_ai_clickpic_setting:deleteBatch', '1', NULL, 0, NULL, 1, 0, 0, 0, NULL, 'admin', '2025-02-25 09:01:19', NULL, NULL, 0, 0, '1', 0);
-- 导出excel
INSERT INTO sys_permission(id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
VALUES ('2025022509019230195', '2025022509019230190', '导出excel_采集图片配置', NULL, NULL, 0, NULL, NULL, 2, 'org.jeecg.modules.demo:tab_ai_clickpic_setting:exportXls', '1', NULL, 0, NULL, 1, 0, 0, 0, NULL, 'admin', '2025-02-25 09:01:19', NULL, NULL, 0, 0, '1', 0);
-- 导入excel
INSERT INTO sys_permission(id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
VALUES ('2025022509019230196', '2025022509019230190', '导入excel_采集图片配置', NULL, NULL, 0, NULL, NULL, 2, 'org.jeecg.modules.demo:tab_ai_clickpic_setting:importExcel', '1', NULL, 0, NULL, 1, 0, 0, 0, NULL, 'admin', '2025-02-25 09:01:19', NULL, NULL, 0, 0, '1', 0);