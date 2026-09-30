-- =====================================================================
--  MES-MAX  ·  CNC 数控加工行业制造执行系统  ·  建表脚本
--  兼容：H2 2.x (MODE=MySQL)  /  MySQL 8.0
--  说明：全部使用 CREATE TABLE IF NOT EXISTS，可重复执行
-- =====================================================================

-- =========================== 1. 系统与权限 ===========================

CREATE TABLE IF NOT EXISTS sys_user (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  username        VARCHAR(50)  NOT NULL,
  password        VARCHAR(120) NOT NULL,
  real_name       VARCHAR(50),
  emp_no          VARCHAR(30),
  role_code       VARCHAR(50),
  dept_name       VARCHAR(80),
  phone           VARCHAR(30),
  status          TINYINT      DEFAULT 1,
  last_login_time DATETIME,
  create_time     DATETIME,
  update_time     DATETIME
);

CREATE TABLE IF NOT EXISTS sys_role (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  role_code   VARCHAR(50)  NOT NULL,
  role_name   VARCHAR(50),
  perms       VARCHAR(1000),
  home_page   VARCHAR(120),
  status      TINYINT DEFAULT 1,
  remark      VARCHAR(300),
  create_time DATETIME
);

CREATE TABLE IF NOT EXISTS sys_login_log (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  username   VARCHAR(50),
  ip         VARCHAR(60),
  browser    VARCHAR(160),
  status     TINYINT,
  msg        VARCHAR(300),
  login_time DATETIME
);

CREATE TABLE IF NOT EXISTS sys_oper_log (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  module     VARCHAR(60),
  action     VARCHAR(60),
  uri        VARCHAR(200),
  method     VARCHAR(10),
  params     VARCHAR(2000),
  username   VARCHAR(50),
  ip         VARCHAR(60),
  cost_ms    BIGINT,
  status     TINYINT,
  msg        VARCHAR(500),
  oper_time  DATETIME
);

CREATE TABLE IF NOT EXISTS sys_dict (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  dict_type  VARCHAR(60),
  dict_label VARCHAR(80),
  dict_value VARCHAR(60),
  sort_no    INT DEFAULT 0,
  color      VARCHAR(20),
  status     TINYINT DEFAULT 1
);

-- =========================== 2. 制造主数据 ===========================

CREATE TABLE IF NOT EXISTS md_workshop (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  ws_code     VARCHAR(30) NOT NULL,
  ws_name     VARCHAR(80),
  manager     VARCHAR(50),
  phone       VARCHAR(30),
  area_sqm    DECIMAL(10,2),
  status      TINYINT DEFAULT 1,
  remark      VARCHAR(300)
);

CREATE TABLE IF NOT EXISTS md_work_center (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  wc_code       VARCHAR(30) NOT NULL,
  wc_name       VARCHAR(80),
  workshop_id   BIGINT,
  wc_type       VARCHAR(30),
  capacity_hour DECIMAL(8,2),
  status        TINYINT DEFAULT 1,
  remark        VARCHAR(300)
);

CREATE TABLE IF NOT EXISTS md_machine (
  id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
  mac_code              VARCHAR(30) NOT NULL,
  mac_name              VARCHAR(80),
  mac_model             VARCHAR(60),
  brand                 VARCHAR(60),
  cnc_type              VARCHAR(30),
  work_center_id        BIGINT,
  workshop_id           BIGINT,
  axis_count            INT,
  max_spindle_speed     INT,
  travel_x              DECIMAL(10,2),
  travel_y              DECIMAL(10,2),
  travel_z              DECIMAL(10,2),
  tool_magazine_capacity INT,
  controller_type       VARCHAR(40),
  protocol              VARCHAR(30),
  ip_addr               VARCHAR(40),
  status                VARCHAR(20),
  run_state             VARCHAR(20),
  oee_target            DECIMAL(5,2),
  purchase_date         DATE,
  remark                VARCHAR(300)
);

CREATE TABLE IF NOT EXISTS md_material (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  mat_code        VARCHAR(40) NOT NULL,
  mat_name        VARCHAR(100),
  spec            VARCHAR(120),
  material_grade  VARCHAR(60),
  unit            VARCHAR(16),
  mat_type        VARCHAR(30),
  drawing_no      VARCHAR(60),
  safe_stock      DECIMAL(12,2),
  current_stock   DECIMAL(12,2),
  unit_cost       DECIMAL(12,2),
  standard_price  DECIMAL(12,2),
  process_route   VARCHAR(200),
  status          TINYINT DEFAULT 1,
  remark          VARCHAR(300)
);

CREATE TABLE IF NOT EXISTS md_bom (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  bom_code       VARCHAR(40) NOT NULL,
  bom_name       VARCHAR(100),
  product_mat_id BIGINT,
  version        VARCHAR(20),
  status         TINYINT DEFAULT 1,
  remark         VARCHAR(300)
);

CREATE TABLE IF NOT EXISTS md_bom_item (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  bom_id     BIGINT,
  mat_id     BIGINT,
  mat_code   VARCHAR(40),
  mat_name   VARCHAR(100),
  qty_per    DECIMAL(12,4),
  scrap_rate DECIMAL(6,3),
  item_type  VARCHAR(30),
  remark     VARCHAR(300)
);

CREATE TABLE IF NOT EXISTS md_process (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  proc_code       VARCHAR(30) NOT NULL,
  proc_name       VARCHAR(80),
  proc_type       VARCHAR(30),
  is_inspection   TINYINT DEFAULT 0,
  is_outsource    TINYINT DEFAULT 0,
  default_wc_type VARCHAR(30),
  sort_no         INT DEFAULT 0,
  remark          VARCHAR(300)
);

CREATE TABLE IF NOT EXISTS md_routing (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  routing_code   VARCHAR(40) NOT NULL,
  routing_name   VARCHAR(100),
  product_mat_id BIGINT,
  version        VARCHAR(20),
  status         TINYINT DEFAULT 1,
  remark         VARCHAR(300)
);

CREATE TABLE IF NOT EXISTS md_routing_step (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  routing_id     BIGINT,
  seq_no         INT,
  proc_id        BIGINT,
  proc_code      VARCHAR(30),
  proc_name      VARCHAR(80),
  wc_id          BIGINT,
  wc_name        VARCHAR(80),
  setup_time     DECIMAL(10,2),
  piece_time     DECIMAL(10,2),
  nc_program_no  VARCHAR(60),
  nc_program_ver VARCHAR(20),
  is_key_proc    TINYINT DEFAULT 0,
  is_inspection  TINYINT DEFAULT 0,
  tool_requirement VARCHAR(200),
  remark         VARCHAR(300)
);

CREATE TABLE IF NOT EXISTS md_customer (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  cust_code  VARCHAR(30) NOT NULL,
  cust_name  VARCHAR(100),
  contact    VARCHAR(50),
  phone      VARCHAR(30),
  cust_level VARCHAR(10),
  industry   VARCHAR(60),
  address    VARCHAR(200),
  status     TINYINT DEFAULT 1,
  remark     VARCHAR(300)
);

CREATE TABLE IF NOT EXISTS md_employee (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  emp_no      VARCHAR(30) NOT NULL,
  emp_name    VARCHAR(50),
  dept_name   VARCHAR(80),
  post        VARCHAR(50),
  skill_level VARCHAR(30),
  team_name   VARCHAR(50),
  phone       VARCHAR(30),
  hire_date   DATE,
  status      TINYINT DEFAULT 1
);

CREATE TABLE IF NOT EXISTS md_shift (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  shift_code    VARCHAR(20) NOT NULL,
  shift_name    VARCHAR(40),
  start_time    VARCHAR(10),
  end_time      VARCHAR(10),
  cross_day     TINYINT DEFAULT 0,
  break_minutes INT DEFAULT 0,
  status        TINYINT DEFAULT 1
);

-- =========================== 3. 生产执行 ===========================

CREATE TABLE IF NOT EXISTS prod_order (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_no       VARCHAR(40) NOT NULL,
  customer_id    BIGINT,
  customer_name  VARCHAR(100),
  product_mat_id BIGINT,
  product_code   VARCHAR(40),
  product_name   VARCHAR(100),
  product_spec   VARCHAR(120),
  plan_qty       DECIMAL(12,2),
  finished_qty   DECIMAL(12,2) DEFAULT 0,
  qualified_qty  DECIMAL(12,2) DEFAULT 0,
  defect_qty     DECIMAL(12,2) DEFAULT 0,
  plan_start     DATETIME,
  plan_end       DATETIME,
  priority       VARCHAR(20),
  priority_score INT DEFAULT 50,
  routing_id     BIGINT,
  order_type     VARCHAR(30),
  status         VARCHAR(30),
  source         VARCHAR(30),
  sales_no       VARCHAR(40),
  nc_program_no  VARCHAR(60),
  is_locked      TINYINT DEFAULT 0,
  create_time    DATETIME,
  update_time    DATETIME,
  remark         VARCHAR(300)
);

CREATE TABLE IF NOT EXISTS prod_order_step (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id       BIGINT,
  order_no       VARCHAR(40),
  seq_no         INT,
  proc_id        BIGINT,
  proc_code      VARCHAR(30),
  proc_name      VARCHAR(80),
  wc_id          BIGINT,
  wc_name        VARCHAR(80),
  machine_id     BIGINT,
  machine_code   VARCHAR(30),
  plan_start     DATETIME,
  plan_end       DATETIME,
  actual_start   DATETIME,
  actual_end     DATETIME,
  plan_qty       DECIMAL(12,2),
  finished_qty   DECIMAL(12,2) DEFAULT 0,
  qualified_qty  DECIMAL(12,2) DEFAULT 0,
  defect_qty     DECIMAL(12,2) DEFAULT 0,
  setup_time     DECIMAL(10,2),
  piece_time     DECIMAL(10,2),
  run_minutes    DECIMAL(12,2) DEFAULT 0,
  stop_minutes   DECIMAL(12,2) DEFAULT 0,
  nc_program_no  VARCHAR(60),
  nc_program_ver VARCHAR(20),
  tool_requirement VARCHAR(200),
  is_key_proc    TINYINT DEFAULT 0,
  is_inspection  TINYINT DEFAULT 0,
  status         VARCHAR(30),
  remark         VARCHAR(300)
);

CREATE TABLE IF NOT EXISTS prod_dispatch (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  dispatch_no   VARCHAR(40) NOT NULL,
  order_id      BIGINT,
  order_no      VARCHAR(40),
  step_id       BIGINT,
  seq_no        INT,
  proc_name     VARCHAR(80),
  machine_id    BIGINT,
  machine_code  VARCHAR(30),
  employee_id   BIGINT,
  employee_name VARCHAR(50),
  team_name     VARCHAR(50),
  shift_id      BIGINT,
  shift_name    VARCHAR(40),
  plan_qty      DECIMAL(12,2),
  plan_start    DATETIME,
  plan_end      DATETIME,
  dispatch_time DATETIME,
  receive_time  DATETIME,
  status        VARCHAR(30),
  nc_program_no VARCHAR(60),
  remark        VARCHAR(300)
);

CREATE TABLE IF NOT EXISTS prod_task_report (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  report_no     VARCHAR(40),
  dispatch_id   BIGINT,
  order_id      BIGINT,
  order_no      VARCHAR(40),
  step_id       BIGINT,
  machine_id    BIGINT,
  machine_code  VARCHAR(30),
  employee_id   BIGINT,
  employee_name VARCHAR(50),
  shift_id      BIGINT,
  shift_name    VARCHAR(40),
  report_type   VARCHAR(30),
  work_date     DATE,
  start_time    DATETIME,
  end_time      DATETIME,
  run_minutes   DECIMAL(10,2),
  stop_minutes  DECIMAL(10,2),
  qty           DECIMAL(12,2),
  qualified_qty DECIMAL(12,2),
  defect_qty    DECIMAL(12,2),
  scrap_reason  VARCHAR(200),
  nc_program_no VARCHAR(60),
  tool_code     VARCHAR(40),
  tool_offset_no VARCHAR(20),
  report_time   DATETIME,
  remark        VARCHAR(300)
);

CREATE TABLE IF NOT EXISTS prod_stop_record (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  machine_id   BIGINT,
  machine_code VARCHAR(30),
  order_id     BIGINT,
  order_no     VARCHAR(40),
  step_id      BIGINT,
  stop_type    VARCHAR(30),
  reason_code  VARCHAR(30),
  reason_desc  VARCHAR(200),
  start_time   DATETIME,
  end_time     DATETIME,
  duration_min DECIMAL(10,2),
  handler      VARCHAR(50),
  affect_qty   DECIMAL(12,2),
  shift_name   VARCHAR(40),
  status       VARCHAR(20),
  remark       VARCHAR(300)
);

CREATE TABLE IF NOT EXISTS prod_machine_status_log (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  machine_id    BIGINT,
  machine_code  VARCHAR(30),
  status        VARCHAR(20),
  prev_status   VARCHAR(20),
  reason        VARCHAR(200),
  order_no      VARCHAR(40),
  nc_program_no VARCHAR(60),
  start_time    DATETIME,
  end_time      DATETIME,
  duration_sec  BIGINT,
  operator      VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS prod_machine_telemetry (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  machine_id    BIGINT,
  machine_code  VARCHAR(30),
  ts            DATETIME,
  spindle_speed DECIMAL(10,2),
  feed_rate     DECIMAL(10,2),
  feed_override DECIMAL(6,2),
  spindle_load  DECIMAL(6,2),
  axis_x        DECIMAL(12,3),
  axis_y        DECIMAL(12,3),
  axis_z        DECIMAL(12,3),
  program_no    VARCHAR(60),
  tool_no       VARCHAR(20),
  alarm_code    VARCHAR(30),
  run_state     VARCHAR(20),
  part_count    INT,
  power_kw      DECIMAL(8,2),
  coolant_on    TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS prod_andon (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  andon_no     VARCHAR(40),
  machine_id   BIGINT,
  machine_code VARCHAR(30),
  order_id     BIGINT,
  order_no     VARCHAR(40),
  andon_type   VARCHAR(30),
  level        VARCHAR(10),
  content      VARCHAR(300),
  call_user    VARCHAR(50),
  call_time    DATETIME,
  respond_user VARCHAR(50),
  respond_time DATETIME,
  resolve_time DATETIME,
  duration_min DECIMAL(10,2),
  status       VARCHAR(20),
  remark       VARCHAR(300)
);

-- =========================== 4. 质量管理 ===========================

CREATE TABLE IF NOT EXISTS qc_inspection (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  insp_no       VARCHAR(40) NOT NULL,
  insp_type     VARCHAR(30),
  order_id      BIGINT,
  order_no      VARCHAR(40),
  step_id       BIGINT,
  proc_name     VARCHAR(80),
  machine_id    BIGINT,
  machine_code  VARCHAR(30),
  sn_code       VARCHAR(60),
  material_id   BIGINT,
  product_name  VARCHAR(100),
  sample_qty    INT,
  qualified_qty INT,
  defect_qty    INT,
  result        VARCHAR(20),
  inspector     VARCHAR(50),
  insp_time     DATETIME,
  conclusion    VARCHAR(300),
  remark        VARCHAR(300)
);

CREATE TABLE IF NOT EXISTS qc_inspection_item (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  inspection_id BIGINT,
  item_name     VARCHAR(80),
  item_type     VARCHAR(30),
  nominal_value DECIMAL(14,4),
  upper_tol     DECIMAL(14,4),
  lower_tol     DECIMAL(14,4),
  unit          VARCHAR(16),
  measure_value DECIMAL(14,4),
  result        VARCHAR(20),
  gauge_used    VARCHAR(60),
  remark        VARCHAR(200)
);

CREATE TABLE IF NOT EXISTS qc_defect (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  defect_no     VARCHAR(40) NOT NULL,
  order_id      BIGINT,
  order_no      VARCHAR(40),
  step_id       BIGINT,
  machine_id    BIGINT,
  machine_code  VARCHAR(30),
  sn_code       VARCHAR(60),
  material_id   BIGINT,
  product_name  VARCHAR(100),
  defect_type   VARCHAR(40),
  defect_desc   VARCHAR(300),
  defect_qty    DECIMAL(12,2),
  reason_type   VARCHAR(40),
  handle_type   VARCHAR(30),
  handle_status VARCHAR(20),
  handler       VARCHAR(50),
  handle_time   DATETIME,
  cost_loss     DECIMAL(12,2),
  cause_analysis VARCHAR(500),
  image_url     VARCHAR(300),
  create_time   DATETIME,
  remark        VARCHAR(300)
);

CREATE TABLE IF NOT EXISTS qc_spc_sample (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  char_name     VARCHAR(80),
  char_code     VARCHAR(40),
  machine_id    BIGINT,
  machine_code  VARCHAR(30),
  order_id      BIGINT,
  order_no      VARCHAR(40),
  sn_code       VARCHAR(60),
  nominal_value DECIMAL(14,4),
  upper_limit   DECIMAL(14,4),
  lower_limit   DECIMAL(14,4),
  sample_value  DECIMAL(14,4),
  sample_no     INT,
  sample_time   DATETIME,
  inspector     VARCHAR(50),
  remark        VARCHAR(200)
);

-- =========================== 5. 刀具管理 ===========================

CREATE TABLE IF NOT EXISTS tool_ledger (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  tool_code      VARCHAR(40) NOT NULL,
  tool_name      VARCHAR(80),
  tool_type      VARCHAR(40),
  spec           VARCHAR(80),
  material_grade VARCHAR(40),
  coating        VARCHAR(40),
  std_life_min   DECIMAL(10,2),
  used_life_min  DECIMAL(10,2) DEFAULT 0,
  std_life_qty   INT,
  used_qty       INT DEFAULT 0,
  life_ratio     DECIMAL(6,2) DEFAULT 0,
  location       VARCHAR(60),
  machine_id     BIGINT,
  machine_code   VARCHAR(30),
  offset_no      VARCHAR(20),
  status         VARCHAR(20),
  supplier       VARCHAR(80),
  unit_price     DECIMAL(12,2),
  purchase_date  DATE,
  remark         VARCHAR(300)
);

CREATE TABLE IF NOT EXISTS tool_usage (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  tool_id        BIGINT,
  tool_code      VARCHAR(40),
  tool_name      VARCHAR(80),
  machine_id     BIGINT,
  machine_code   VARCHAR(30),
  order_id       BIGINT,
  order_no       VARCHAR(40),
  step_id        BIGINT,
  nc_program_no  VARCHAR(60),
  offset_no      VARCHAR(20),
  mount_time     DATETIME,
  unmount_time   DATETIME,
  use_minutes    DECIMAL(10,2),
  part_qty       INT,
  wear_value     DECIMAL(8,4),
  unmount_reason VARCHAR(60),
  operator       VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS tool_grind (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  tool_id      BIGINT,
  tool_code    VARCHAR(40),
  grind_no     VARCHAR(40),
  grind_type   VARCHAR(30),
  before_spec  VARCHAR(60),
  after_spec   VARCHAR(60),
  grind_amount DECIMAL(8,4),
  grind_time   DATETIME,
  grinder      VARCHAR(50),
  result       VARCHAR(20),
  cost         DECIMAL(10,2),
  remark       VARCHAR(200)
);

CREATE TABLE IF NOT EXISTS tool_measure (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  tool_id      BIGINT,
  tool_code    VARCHAR(40),
  machine_id   BIGINT,
  machine_code VARCHAR(30),
  offset_no    VARCHAR(20),
  radius_value DECIMAL(10,4),
  length_value DECIMAL(10,4),
  deviation    DECIMAL(10,4),
  measure_time DATETIME,
  measurer     VARCHAR(50),
  result       VARCHAR(20),
  remark       VARCHAR(200)
);

-- =========================== 6. 设备维保 ===========================

CREATE TABLE IF NOT EXISTS eq_check_item (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  item_code    VARCHAR(30) NOT NULL,
  item_name    VARCHAR(120),
  mac_type     VARCHAR(30),
  check_part   VARCHAR(60),
  check_method VARCHAR(60),
  standard     VARCHAR(200),
  period_type  VARCHAR(20),
  is_key       TINYINT DEFAULT 0,
  sort_no      INT DEFAULT 0,
  status       TINYINT DEFAULT 1
);

CREATE TABLE IF NOT EXISTS eq_check_record (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  machine_id    BIGINT,
  machine_code  VARCHAR(30),
  item_id       BIGINT,
  item_name     VARCHAR(120),
  check_time    DATETIME,
  checker       VARCHAR(50),
  shift_name    VARCHAR(40),
  result        VARCHAR(20),
  abnormal_desc VARCHAR(300),
  handle_status VARCHAR(20),
  handle_time   DATETIME,
  remark        VARCHAR(200)
);

CREATE TABLE IF NOT EXISTS eq_maint_plan (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  plan_code    VARCHAR(40) NOT NULL,
  plan_name    VARCHAR(100),
  machine_id   BIGINT,
  machine_code VARCHAR(30),
  maint_type   VARCHAR(30),
  period_type  VARCHAR(20),
  period_value INT,
  last_date    DATE,
  next_date    DATE,
  principal    VARCHAR(50),
  content      VARCHAR(500),
  status       VARCHAR(20)
);

CREATE TABLE IF NOT EXISTS eq_maint_order (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  maint_no     VARCHAR(40) NOT NULL,
  plan_id      BIGINT,
  machine_id   BIGINT,
  machine_code VARCHAR(30),
  maint_type   VARCHAR(30),
  fault_desc   VARCHAR(300),
  maintainer   VARCHAR(50),
  plan_start   DATETIME,
  plan_end     DATETIME,
  actual_start DATETIME,
  actual_end   DATETIME,
  down_minutes DECIMAL(10,2),
  spare_parts  VARCHAR(300),
  cost         DECIMAL(12,2),
  result       VARCHAR(300),
  status       VARCHAR(20),
  remark       VARCHAR(300)
);

CREATE TABLE IF NOT EXISTS eq_fault (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  fault_no        VARCHAR(40) NOT NULL,
  machine_id      BIGINT,
  machine_code    VARCHAR(30),
  fault_type      VARCHAR(40),
  fault_code      VARCHAR(30),
  fault_desc      VARCHAR(300),
  report_user     VARCHAR(50),
  report_time     DATETIME,
  respond_time    DATETIME,
  repair_end_time DATETIME,
  down_minutes    DECIMAL(10,2),
  cause           VARCHAR(300),
  solution        VARCHAR(400),
  spare_parts     VARCHAR(300),
  repair_user     VARCHAR(50),
  status          VARCHAR(20),
  remark          VARCHAR(300)
);

-- =========================== 7. 谱系追溯 ===========================

CREATE TABLE IF NOT EXISTS trace_sn (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  sn_code       VARCHAR(60) NOT NULL,
  order_id      BIGINT,
  order_no      VARCHAR(40),
  material_id   BIGINT,
  product_code  VARCHAR(40),
  product_name  VARCHAR(100),
  batch_no      VARCHAR(60),
  current_step  INT,
  current_proc  VARCHAR(80),
  machine_code  VARCHAR(30),
  status        VARCHAR(20),
  qc_result     VARCHAR(20),
  create_time   DATETIME,
  complete_time DATETIME,
  remark        VARCHAR(300)
);

CREATE TABLE IF NOT EXISTS trace_log (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  sn_code        VARCHAR(60),
  order_id       BIGINT,
  order_no       VARCHAR(40),
  step_id        BIGINT,
  seq_no         INT,
  proc_name      VARCHAR(80),
  machine_id     BIGINT,
  machine_code   VARCHAR(30),
  employee_id    BIGINT,
  operator       VARCHAR(50),
  nc_program_no  VARCHAR(60),
  tool_code      VARCHAR(40),
  tool_offset_no VARCHAR(20),
  material_batch VARCHAR(60),
  start_time     DATETIME,
  end_time       DATETIME,
  qty            DECIMAL(12,2),
  run_minutes    DECIMAL(10,2),
  qc_result      VARCHAR(20),
  param_json     VARCHAR(600),
  shift_name     VARCHAR(40),
  remark         VARCHAR(300)
);

CREATE TABLE IF NOT EXISTS trace_material_batch (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  batch_no   VARCHAR(60) NOT NULL,
  mat_id     BIGINT,
  mat_code   VARCHAR(40),
  mat_name   VARCHAR(100),
  spec       VARCHAR(120),
  supplier   VARCHAR(80),
  heat_no    VARCHAR(60),
  qty        DECIMAL(12,2),
  unit       VARCHAR(16),
  cert_no    VARCHAR(60),
  in_time    DATETIME,
  status     VARCHAR(20),
  remark     VARCHAR(300)
);

-- =========================== 索引 ===========================

CREATE INDEX IF NOT EXISTS idx_telemetry_ts       ON prod_machine_telemetry (machine_id, ts);
CREATE INDEX IF NOT EXISTS idx_report_date        ON prod_task_report (work_date, machine_id);
CREATE INDEX IF NOT EXISTS idx_step_order         ON prod_order_step (order_id);
CREATE INDEX IF NOT EXISTS idx_statuslog_machine  ON prod_machine_status_log (machine_id, start_time);
CREATE INDEX IF NOT EXISTS idx_tracelog_sn        ON trace_log (sn_code);
CREATE INDEX IF NOT EXISTS idx_defect_order       ON qc_defect (order_id);
CREATE INDEX IF NOT EXISTS idx_stop_machine       ON prod_stop_record (machine_id, start_time);
