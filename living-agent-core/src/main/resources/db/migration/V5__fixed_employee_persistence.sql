-- Fixed Digital Employee Persistence
-- Stores long-lived fixed employee definitions, employee records, and visual/personality personas.

CREATE TABLE IF NOT EXISTS fixed_employee_definition (
    code VARCHAR(16) PRIMARY KEY,
    employee_id VARCHAR(36) UNIQUE REFERENCES enterprise_employees(employee_id) ON DELETE SET NULL,
    name_zh VARCHAR(100) NOT NULL,
    name_en VARCHAR(100),
    title_zh VARCHAR(100) NOT NULL,
    title_en VARCHAR(100),
    department_code VARCHAR(50) NOT NULL,
    department_name VARCHAR(100),
    neuron_id VARCHAR(100),
    channel VARCHAR(100),
    roles JSONB DEFAULT '[]'::jsonb,
    capabilities JSONB DEFAULT '[]'::jsonb,
    tools JSONB DEFAULT '[]'::jsonb,
    required_skills JSONB DEFAULT '[]'::jsonb,
    personality JSONB DEFAULT '{}'::jsonb,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_fixed_employee_definition_department ON fixed_employee_definition(department_code);
CREATE INDEX IF NOT EXISTS idx_fixed_employee_definition_active ON fixed_employee_definition(active);
CREATE INDEX IF NOT EXISTS idx_fixed_employee_definition_employee ON fixed_employee_definition(employee_id);

CREATE TABLE IF NOT EXISTS fixed_employee_profile (
    code VARCHAR(16) PRIMARY KEY REFERENCES fixed_employee_definition(code) ON DELETE CASCADE,
    employee_id VARCHAR(36) UNIQUE REFERENCES enterprise_employees(employee_id) ON DELETE CASCADE,
    display_name_zh VARCHAR(100) NOT NULL,
    display_name_en VARCHAR(100),
    summary_zh TEXT,
    summary_en TEXT,
    traits JSONB DEFAULT '[]'::jsonb,
    tool_tags JSONB DEFAULT '[]'::jsonb,
    long_term_memory JSONB DEFAULT '{}'::jsonb,
    preferences JSONB DEFAULT '{}'::jsonb,
    current_task TEXT,
    status VARCHAR(32) DEFAULT 'active',
    last_active_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_fixed_employee_profile_status ON fixed_employee_profile(status);
CREATE INDEX IF NOT EXISTS idx_fixed_employee_profile_employee ON fixed_employee_profile(employee_id);

CREATE TABLE IF NOT EXISTS fixed_employee_persona (
    code VARCHAR(16) PRIMARY KEY REFERENCES fixed_employee_definition(code) ON DELETE CASCADE,
    employee_id VARCHAR(36) UNIQUE REFERENCES enterprise_employees(employee_id) ON DELETE CASCADE,
    icon VARCHAR(32) DEFAULT '🤖',
    hair VARCHAR(32) DEFAULT 'short',
    glasses BOOLEAN DEFAULT FALSE,
    badge_style VARCHAR(32) DEFAULT 'classic',
    stance VARCHAR(32) DEFAULT 'focused',
    outfit VARCHAR(32) DEFAULT 'default',
    accent_color VARCHAR(32) DEFAULT '#58a6ff',
    face VARCHAR(32) DEFAULT 'neutral',
    skin_tone VARCHAR(32) DEFAULT '#f5d0b1',
    body_shape VARCHAR(32) DEFAULT 'default',
    clothing_variant VARCHAR(32) DEFAULT 'standard',
    accessory_variant VARCHAR(32) DEFAULT 'none',
    badge_label VARCHAR(100),
    avatar_style JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_fixed_employee_persona_outfit ON fixed_employee_persona(outfit);

CREATE TRIGGER update_fixed_employee_definition_updated_at BEFORE UPDATE ON fixed_employee_definition
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_fixed_employee_profile_updated_at BEFORE UPDATE ON fixed_employee_profile
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_fixed_employee_persona_updated_at BEFORE UPDATE ON fixed_employee_persona
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Ensure every fixed digital employee also has a normal enterprise employee record.
INSERT INTO enterprise_employees (employee_id, name, department_id, department_name, position, identity, access_level, avatar_url, join_date, active, tenant_id, sync_source)
VALUES
    ('fixed_T01', '真砺', 'dept_tech', '技术部', '代码审查员', 'FIXED_DIGITAL_EMPLOYEE', 'DEPARTMENT', '💻', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed'),
    ('fixed_T02', '真构', 'dept_tech', '技术部', '架构师', 'FIXED_DIGITAL_EMPLOYEE', 'DEPARTMENT', '🧠', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed'),
    ('fixed_T03', '真捷', 'dept_tech', '技术部', 'DevOps工程师', 'FIXED_DIGITAL_EMPLOYEE', 'DEPARTMENT', '🛠️', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed'),
    ('fixed_T04', '真稳', 'dept_tech', '技术部', '运维工程师', 'FIXED_DIGITAL_EMPLOYEE', 'DEPARTMENT', '🖥️', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed'),
    ('fixed_T05', '真模', 'dept_tech', '技术部', 'AI模型管理员', 'FIXED_DIGITAL_EMPLOYEE', 'DEPARTMENT', '🤖', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed'),
    ('fixed_T06', '真续', 'dept_tech', '技术部', '状态管理员', 'FIXED_DIGITAL_EMPLOYEE', 'DEPARTMENT', '📡', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed'),
    ('fixed_T07', '真盾', 'dept_tech', '技术部', '安全工程师', 'FIXED_DIGITAL_EMPLOYEE', 'DEPARTMENT', '🛡️', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed'),
    ('fixed_T08', '真策', 'dept_tech', '技术部', '配置管理员', 'FIXED_DIGITAL_EMPLOYEE', 'DEPARTMENT', '⚙️', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed'),
    ('fixed_T09', '真绘', 'dept_tech', '技术部', '前端工程师', 'FIXED_DIGITAL_EMPLOYEE', 'DEPARTMENT', '🎨', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed'),
    ('fixed_T10', '真栈', 'dept_tech', '技术部', '后端工程师', 'FIXED_DIGITAL_EMPLOYEE', 'DEPARTMENT', '🗄️', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed'),
    ('fixed_F01', '真账', 'dept_finance', '财务部', '财务会计', 'FIXED_DIGITAL_EMPLOYEE', 'DEPARTMENT', '💰', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed'),
    ('fixed_F02', '真审', 'dept_finance', '财务部', '报销审核员', 'FIXED_DIGITAL_EMPLOYEE', 'DEPARTMENT', '🧾', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed'),
    ('fixed_F03', '真算', 'dept_finance', '财务部', '成本核算员', 'FIXED_DIGITAL_EMPLOYEE', 'DEPARTMENT', '📊', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed'),
    ('fixed_F04', '真预', 'dept_finance', '财务部', '预算管理员', 'FIXED_DIGITAL_EMPLOYEE', 'DEPARTMENT', '🏦', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed'),
    ('fixed_O01', '真析', 'dept_ops', '运营部', '数据分析师', 'FIXED_DIGITAL_EMPLOYEE', 'DEPARTMENT', '📈', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed'),
    ('fixed_O02', '真营', 'dept_ops', '运营部', '运营专员', 'FIXED_DIGITAL_EMPLOYEE', 'DEPARTMENT', '🚚', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed'),
    ('fixed_O03', '真度', 'dept_ops', '运营部', '任务调度员', 'FIXED_DIGITAL_EMPLOYEE', 'DEPARTMENT', '⏱️', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed'),
    ('fixed_O04', '真流', 'dept_ops', '运营部', '流程管理员', 'FIXED_DIGITAL_EMPLOYEE', 'DEPARTMENT', '🧩', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed'),
    ('fixed_S01', '真拓', 'dept_sales', '销售部', '销售代表', 'FIXED_DIGITAL_EMPLOYEE', 'DEPARTMENT', '📣', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed'),
    ('fixed_S02', '真宣', 'dept_sales', '销售部', '市场专员', 'FIXED_DIGITAL_EMPLOYEE', 'DEPARTMENT', '🎯', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed'),
    ('fixed_S03', '真联', 'dept_sales', '销售部', '渠道经理', 'FIXED_DIGITAL_EMPLOYEE', 'DEPARTMENT', '🤝', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed'),
    ('fixed_H01', '真才', 'dept_hr', '人力资源部', '招聘专员', 'FIXED_DIGITAL_EMPLOYEE', 'DEPARTMENT', '👥', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed'),
    ('fixed_H02', '真绩', 'dept_hr', '人力资源部', '绩效管理员', 'FIXED_DIGITAL_EMPLOYEE', 'DEPARTMENT', '📝', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed'),
    ('fixed_C01', '真晴', 'dept_cs', '客服部', '客服专员', 'FIXED_DIGITAL_EMPLOYEE', 'DEPARTMENT', '🎧', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed'),
    ('fixed_C02', '真修', 'dept_cs', '客服部', '工单处理员', 'FIXED_DIGITAL_EMPLOYEE', 'DEPARTMENT', '🧰', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed'),
    ('fixed_A01', '真序', 'dept_admin', '行政部', '行政助理', 'FIXED_DIGITAL_EMPLOYEE', 'DEPARTMENT', '📋', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed'),
    ('fixed_A02', '真典', 'dept_admin', '行政部', '文档管理员', 'FIXED_DIGITAL_EMPLOYEE', 'DEPARTMENT', '📚', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed'),
    ('fixed_A03', '真笔', 'dept_admin', '行政部', '文案策划', 'FIXED_DIGITAL_EMPLOYEE', 'DEPARTMENT', '✍️', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed'),
    ('fixed_L01', '真律', 'dept_legal', '法务部', '合同审查员', 'FIXED_DIGITAL_EMPLOYEE', 'DEPARTMENT', '⚖️', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed'),
    ('fixed_L02', '真规', 'dept_legal', '法务部', '合规专员', 'FIXED_DIGITAL_EMPLOYEE', 'DEPARTMENT', '📜', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed'),
    ('fixed_M01', '真合', 'dept_main', '综合管理', '协调员', 'FIXED_DIGITAL_EMPLOYEE', 'DEPARTMENT', '🎯', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed'),
    ('fixed_M02', '真略', 'dept_main', '综合管理', '战略规划师', 'FIXED_DIGITAL_EMPLOYEE', 'DEPARTMENT', '🧭', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed')
ON CONFLICT (employee_id) DO UPDATE SET
    name = EXCLUDED.name,
    department_id = EXCLUDED.department_id,
    department_name = EXCLUDED.department_name,
    position = EXCLUDED.position,
    identity = EXCLUDED.identity,
    avatar_url = EXCLUDED.avatar_url,
    active = TRUE,
    updated_at = CURRENT_TIMESTAMP;

CREATE OR REPLACE FUNCTION fixed_employee_neuron_uri(dept text, code text)
RETURNS text AS $$
BEGIN
    RETURN 'neuron://' || dept || '/' || lower(code) || '/001';
END;
$$ LANGUAGE plpgsql IMMUTABLE;

CREATE OR REPLACE FUNCTION fixed_employee_channel_uri(dept text, code text)
RETURNS text AS $$
BEGIN
    RETURN 'channel://' || dept || '/' || lower(code);
END;
$$ LANGUAGE plpgsql IMMUTABLE;

INSERT INTO fixed_employee_definition (code, employee_id, name_zh, name_en, title_zh, title_en, department_code, department_name, neuron_id, channel, roles, capabilities, tools, required_skills, personality)
VALUES
    ('T01','fixed_T01','真砺','Zhen Li','代码审查员','Code Reviewer','tech','技术部',fixed_employee_neuron_uri('tech','T01'),fixed_employee_channel_uri('tech','T01'),'["审查","规范","安全"]','["代码质量","规范检查","安全审查"]','["gitlab","github"]','["code-review","security"]','{"conscientiousness":0.95,"openness":0.65,"risk_tolerance":0.2,"agreeableness":0.8}'),
    ('T02','fixed_T02','真构','Zhen Gou','架构师','Architect','tech','技术部','tech-architecture-neuron','tech','["架构","设计","评审"]','["架构设计","技术选型","方案评审"]','["gitlab","jira"]','["architecture"]','{"conscientiousness":0.9,"openness":0.75}'),
    ('T03','fixed_T03','真捷','Zhen Jie','DevOps工程师','DevOps Engineer','tech','技术部','tech-devops-neuron','tech','["部署","流水线","自动化"]','["CI/CD","容器部署","自动化运维"]','["jenkins","docker","gitlab","claude_cli"]','["devops"]','{"openness":0.72,"conscientiousness":0.82}'),
    ('T04','fixed_T04','真稳','Zhen Wen','运维工程师','Operations Engineer','tech','技术部','tech-ops-neuron','tech','["监控","资源","调度"]','["监控告警","资源调度","心跳服务"]','["proactive_agent","docker"]','["ops"]','{"conscientiousness":0.88}'),
    ('T05','fixed_T05','真模','Zhen Mo','AI模型管理员','AI Model Manager','tech','技术部','model-admin-neuron','tech','["模型","适配","监控"]','["模型注册","模型切换","性能监控"]','["huggingface"]','["model-management"]','{"conscientiousness":0.86,"openness":0.78}'),
    ('T06','fixed_T06','真续','Zhen Xu','状态管理员','State Manager','tech','技术部','state-neuron','tech','["会话","持久化","恢复"]','["状态维护","会话恢复","上下文管理"]','["knowledge_graph"]','["state"]','{"agreeableness":0.72,"conscientiousness":0.8}'),
    ('T07','fixed_T07','真盾','Zhen Dun','安全工程师','Security Engineer','tech','技术部','security-neuron','tech','["安全","隔离","审计"]','["沙箱执行","风险防护","安全审计"]','["self_improving"]','["security"]','{"conscientiousness":0.96}'),
    ('T08','fixed_T08','真策','Zhen Ce','配置管理员','Configuration Manager','tech','技术部','config-neuron','tech','["配置","版本","回滚"]','["版本控制","配置审计","回滚"]','["notion"]','["config"]','{"conscientiousness":0.84}'),
    ('T09','fixed_T09','真绘','Zhen Hui','前端工程师','Frontend Engineer','tech','技术部','frontend-neuron','tech','["UI","交互","体验"]','["前端交互","UI优化","体验设计"]','["gitlab","browser_automation"]','["frontend"]','{"extroversion":0.76,"openness":0.82}'),
    ('T10','fixed_T10','真栈','Zhen Zhan','后端工程师','Backend Engineer','tech','技术部','backend-neuron','tech','["API","数据库","性能"]','["API设计","数据库优化","服务性能"]','["gitlab","knowledge_graph"]','["backend"]','{"conscientiousness":0.82}'),
    ('F01','fixed_F01','真账','Zhen Zhang','财务会计','Accountant','finance','财务部','finance-accounting-neuron','finance','["账务","报表","税务"]','["账务处理","财务报表","税务整理"]','["invoice_processing"]','["finance"]','{"conscientiousness":0.94}'),
    ('F02','fixed_F02','真审','Zhen Shen','报销审核员','Expense Auditor','finance','财务部','finance-audit-neuron','finance','["审批","核验","合规"]','["报销审批","发票核验","合规检查"]','["invoice_processing","browser_automation"]','["audit"]','{"conscientiousness":0.93}'),
    ('F03','fixed_F03','真算','Zhen Suan','成本核算员','Cost Analyst','finance','财务部','finance-cost-neuron','finance','["成本","核算","分析"]','["成本分析","项目核算","Token成本"]','["summarize"]','["costing"]','{"conscientiousness":0.86}'),
    ('F04','fixed_F04','真预','Zhen Yu','预算管理员','Budget Manager','finance','财务部','finance-budget-neuron','finance','["预算","预警","报告"]','["预算管理","超支预警","预算报告"]','["budget_management"]','["budget"]','{"conscientiousness":0.9}'),
    ('O01','fixed_O01','真析','Zhen Xi','数据分析师','Data Analyst','ops','运营部','ops-analytics-neuron','ops','["分析","报表","预测"]','["数据分析","趋势预测","报表生成"]','["summarize"]','["analytics"]','{"openness":0.72,"conscientiousness":0.82}'),
    ('O02','fixed_O02','真营','Zhen Ying','运营专员','Operations Specialist','ops','运营部','ops-specialist-neuron','ops','["运营","活动","用户"]','["日常运营","用户运营","活动执行"]','["notion","summarize"]','["operations"]','{"extroversion":0.76,"agreeableness":0.78}'),
    ('O03','fixed_O03','真度','Zhen Du','任务调度员','Task Scheduler','ops','运营部','ops-scheduler-neuron','ops','["调度","分配","冲突"]','["任务检出","任务分配","冲突避免"]','["proactive_agent"]','["scheduler"]','{"conscientiousness":0.88}'),
    ('O04','fixed_O04','真流','Zhen Liu','流程管理员','Process Manager','ops','运营部','ops-process-neuron','ops','["流程","队列","优先级"]','["流程队列","优先级调度","流程维护"]','["proactive_agent"]','["process"]','{"conscientiousness":0.82}'),
    ('S01','fixed_S01','真拓','Zhen Tuo','销售代表','Sales Representative','sales','销售部','sales-rep-neuron','sales','["开发","跟进","签约"]','["客户开发","客户跟进","签约推进"]','["notion","slack"]','["sales"]','{"extroversion":0.86,"agreeableness":0.76}'),
    ('S02','fixed_S02','真宣','Zhen Xuan','市场专员','Marketing Specialist','sales','销售部','sales-marketing-neuron','sales','["调研","推广","品牌"]','["市场调研","品牌推广","内容传播"]','["summarize","searxng"]','["marketing"]','{"openness":0.84,"extroversion":0.78}'),
    ('S03','fixed_S03','真联','Zhen Lian','渠道经理','Channel Manager','sales','销售部','sales-channel-neuron','sales','["渠道","集成","协同"]','["渠道管理","平台集成","伙伴协同"]','["github","browser_automation"]','["channel"]','{"extroversion":0.72,"conscientiousness":0.8}'),
    ('H01','fixed_H01','真才','Zhen Cai','招聘专员','Recruiter','hr','人力资源部','hr-recruiting-neuron','hr','["招聘","筛选","面试"]','["人才筛选","招聘管理","面试安排"]','["notion","slack"]','["recruiting"]','{"agreeableness":0.86,"extroversion":0.76}'),
    ('H02','fixed_H02','真绩','Zhen Ji','绩效管理员','Performance Manager','hr','人力资源部','hr-performance-neuron','hr','["绩效","培训","发展"]','["绩效考核","培训发展","员工成长"]','["notion","summarize"]','["performance"]','{"conscientiousness":0.9}'),
    ('C01','fixed_C01','真晴','Zhen Qing','客服专员','Support Specialist','cs','客服部','cs-support-neuron','cs','["咨询","解答","投诉"]','["客户咨询","问题解答","投诉处理"]','["notion","slack"]','["support"]','{"agreeableness":0.9,"extroversion":0.72}'),
    ('C02','fixed_C02','真修','Zhen Xiu','工单处理员','Ticket Handler','cs','客服部','cs-ticket-neuron','cs','["工单","跟踪","升级"]','["工单跟踪","服务升级","问题闭环"]','["notion","jira"]','["ticket"]','{"conscientiousness":0.84}'),
    ('A01','fixed_A01','真序','Zhen Xu','行政助理','Administrative Assistant','admin','行政部','admin-assistant-neuron','admin','["行政","日程","会议"]','["行政事务","日程安排","会议协调"]','["notion","slack"]','["admin"]','{"agreeableness":0.82}'),
    ('A02','fixed_A02','真典','Zhen Dian','文档管理员','Document Manager','admin','行政部','admin-docs-neuron','admin','["文档","档案","归档"]','["文档管理","档案整理","知识归档"]','["office","notion"]','["docs"]','{"conscientiousness":0.86}'),
    ('A03','fixed_A03','真笔','Zhen Bi','文案策划','Copy Planner','admin','行政部','admin-copy-neuron','admin','["文案","内容","品牌"]','["文案创作","品牌传播","内容策划"]','["office","summarize"]','["copy"]','{"openness":0.82,"extroversion":0.74}'),
    ('L01','fixed_L01','真律','Zhen Lv','合同审查员','Contract Reviewer','legal','法务部','legal-contract-neuron','legal','["合同","风险","条款"]','["合同审查","风险识别","条款建议"]','["office","summarize"]','["legal"]','{"conscientiousness":0.96}'),
    ('L02','fixed_L02','真规','Zhen Gui','合规专员','Compliance Specialist','legal','法务部','legal-compliance-neuron','legal','["合规","政策","预警"]','["合规检查","政策解读","风险预警"]','["summarize"]','["compliance"]','{"conscientiousness":0.92}'),
    ('M01','fixed_M01','真合','Zhen He','协调员','Coordinator','main','综合管理','main-coordinator-neuron','main','["协调","调配","解决"]','["跨部门协调","资源调配","问题解决"]','["slack","proactive_agent"]','["coordination"]','{"agreeableness":0.86,"extroversion":0.78}'),
    ('M02','fixed_M02','真略','Zhen Lue','战略规划师','Strategy Planner','main','综合管理','main-strategy-neuron','main','["战略","目标","决策"]','["战略规划","目标管理","决策支持"]','["summarize"]','["strategy"]','{"openness":0.84,"conscientiousness":0.86}')
ON CONFLICT (code) DO UPDATE SET
    employee_id = EXCLUDED.employee_id,
    name_zh = EXCLUDED.name_zh,
    name_en = EXCLUDED.name_en,
    title_zh = EXCLUDED.title_zh,
    title_en = EXCLUDED.title_en,
    department_code = EXCLUDED.department_code,
    department_name = EXCLUDED.department_name,
    neuron_id = EXCLUDED.neuron_id,
    channel = EXCLUDED.channel,
    roles = EXCLUDED.roles,
    capabilities = EXCLUDED.capabilities,
    tools = EXCLUDED.tools,
    required_skills = EXCLUDED.required_skills,
    personality = EXCLUDED.personality,
    active = TRUE,
    updated_at = CURRENT_TIMESTAMP;

-- Normalize all fixed employee routing identifiers to URI format regardless of seed row source.
UPDATE fixed_employee_definition
SET
    neuron_id = fixed_employee_neuron_uri(department_code, code),
    channel = fixed_employee_channel_uri(department_code, code),
    personality = COALESCE(personality, '{}'::jsonb)
        || jsonb_build_object(
            'risk_tolerance', COALESCE((personality->>'risk_tolerance')::numeric, 0.4),
            'agreeableness', COALESCE((personality->>'agreeableness')::numeric, 0.75)
        ),
    updated_at = CURRENT_TIMESTAMP
WHERE code LIKE '_%';

INSERT INTO fixed_employee_profile (code, employee_id, display_name_zh, display_name_en, summary_zh, summary_en, traits, tool_tags, status, last_active_at)
SELECT code, employee_id, name_zh, name_en, title_zh || '，长期固定数字员工画像。', title_en || ', persistent fixed digital employee profile.', roles, tools, 'active', CURRENT_TIMESTAMP
FROM fixed_employee_definition
ON CONFLICT (code) DO UPDATE SET
    employee_id = EXCLUDED.employee_id,
    display_name_zh = EXCLUDED.display_name_zh,
    display_name_en = EXCLUDED.display_name_en,
    summary_zh = EXCLUDED.summary_zh,
    summary_en = EXCLUDED.summary_en,
    traits = EXCLUDED.traits,
    tool_tags = EXCLUDED.tool_tags,
    status = 'active',
    last_active_at = CURRENT_TIMESTAMP,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO fixed_employee_persona (code, employee_id, icon, hair, glasses, badge_style, stance, outfit, accent_color, face, body_shape, clothing_variant, accessory_variant, badge_label)
VALUES
    ('T01','fixed_T01','💻','short',TRUE,'shield','strict','tech','#34d399','serious','default','engineer','glasses','Code Review'),
    ('T02','fixed_T02','🧠','side',TRUE,'classic','focused','tech','#22d3ee','neutral','slim','architect','glasses','Architecture'),
    ('T03','fixed_T03','🛠️','cap',FALSE,'compact','busy','tech','#10b981','neutral','broad','devops','cap','DevOps'),
    ('T04','fixed_T04','🖥️','short',FALSE,'classic','busy','tech','#14b8a6','neutral','broad','ops','headset','Ops'),
    ('T05','fixed_T05','🤖','clean',TRUE,'text','focused','tech','#8b5cf6','neutral','slim','model','glasses','Model Core'),
    ('T06','fixed_T06','📡','clean',FALSE,'round','calm','tech','#38bdf8','neutral','default','state','badge','State'),
    ('T07','fixed_T07','🛡️','short',TRUE,'shield','strict','tech','#0ea5e9','serious','broad','security','glasses','Security'),
    ('T08','fixed_T08','⚙️','clean',FALSE,'compact','focused','tech','#60a5fa','neutral','default','config','badge','Config'),
    ('T09','fixed_T09','🎨','side',FALSE,'classic','friendly','tech','#a78bfa','smile','compact','frontend','badge','Frontend'),
    ('T10','fixed_T10','🗄️','short',TRUE,'classic','focused','tech','#22c55e','neutral','default','backend','glasses','Backend'),
    ('F01','fixed_F01','💰','bun',TRUE,'classic','strict','finance','#60a5fa','serious','slim','formal','glasses','Finance'),
    ('F02','fixed_F02','🧾','clean',TRUE,'shield','focused','finance','#38bdf8','serious','slim','audit','glasses','Audit'),
    ('F03','fixed_F03','📊','short',FALSE,'text','busy','finance','#2563eb','neutral','default','analyst','badge','Costing'),
    ('F04','fixed_F04','🏦','side',TRUE,'compact','focused','finance','#1d4ed8','neutral','slim','budget','glasses','Budget'),
    ('O01','fixed_O01','📈','short',TRUE,'classic','focused','ops','#f59e0b','neutral','default','analyst','glasses','Analytics'),
    ('O02','fixed_O02','🚚','cap',FALSE,'compact','busy','ops','#fb923c','smile','broad','field','cap','Operations'),
    ('O03','fixed_O03','⏱️','clean',TRUE,'round','focused','ops','#f97316','serious','default','scheduler','glasses','Scheduler'),
    ('O04','fixed_O04','🧩','short',FALSE,'text','calm','ops','#ea580c','neutral','default','process','badge','Process'),
    ('S01','fixed_S01','📣','side',FALSE,'classic','friendly','sales','#fb7185','smile','compact','sales','badge','Sales'),
    ('S02','fixed_S02','🎯','curly',FALSE,'compact','busy','sales','#f43f5e','smile','compact','marketing','badge','Marketing'),
    ('S03','fixed_S03','🤝','short',TRUE,'shield','focused','sales','#be123c','neutral','default','channel','glasses','Channel'),
    ('H01','fixed_H01','👥','bun',FALSE,'classic','friendly','hr','#f472b6','smile','compact','hr','badge','Hiring'),
    ('H02','fixed_H02','📝','clean',TRUE,'text','strict','hr','#ec4899','serious','slim','performance','glasses','HR'),
    ('C01','fixed_C01','🎧','short',FALSE,'round','friendly','support','#a78bfa','smile','compact','support','headset','Support'),
    ('C02','fixed_C02','🧰','cap',TRUE,'compact','busy','support','#8b5cf6','neutral','broad','ticket','cap','Tickets'),
    ('A01','fixed_A01','📋','bun',FALSE,'classic','calm','admin','#c084fc','smile','compact','admin','badge','Admin'),
    ('A02','fixed_A02','📚','clean',TRUE,'text','focused','admin','#a855f7','neutral','slim','docs','glasses','Docs'),
    ('A03','fixed_A03','✍️','side',FALSE,'compact','friendly','admin','#d946ef','smile','compact','copy','badge','Copy'),
    ('L01','fixed_L01','⚖️','clean',TRUE,'shield','strict','legal','#f87171','serious','slim','legal','glasses','Legal'),
    ('L02','fixed_L02','📜','bun',TRUE,'classic','focused','legal','#ef4444','neutral','slim','compliance','glasses','Compliance'),
    ('M01','fixed_M01','🎯','short',FALSE,'round','friendly','default','#60a5fa','smile','default','coord','badge','Coord'),
    ('M02','fixed_M02','🧭','side',TRUE,'shield','focused','default','#38bdf8','neutral','slim','strategy','glasses','Strategy')
ON CONFLICT (code) DO UPDATE SET
    employee_id = EXCLUDED.employee_id,
    icon = EXCLUDED.icon,
    hair = EXCLUDED.hair,
    glasses = EXCLUDED.glasses,
    badge_style = EXCLUDED.badge_style,
    stance = EXCLUDED.stance,
    outfit = EXCLUDED.outfit,
    accent_color = EXCLUDED.accent_color,
    face = EXCLUDED.face,
    body_shape = EXCLUDED.body_shape,
    clothing_variant = EXCLUDED.clothing_variant,
    accessory_variant = EXCLUDED.accessory_variant,
    badge_label = EXCLUDED.badge_label,
    updated_at = CURRENT_TIMESTAMP;
