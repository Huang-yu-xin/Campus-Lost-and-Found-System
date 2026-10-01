#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
校园失物招领系统 —— 1 万条真实感模拟数据生成器（perf 压测数据）
生成 tests/performance/mock_data.sql，用 mysql 客户端导入 campus_lost_found 库。

真实性设计：
- 校区/地点联动（南湖/马房山/余家头 各自的地点池）
- 类别加权分布（雨伞/校园卡/钥匙/耳机/水杯…）
- event_time 120 天内指数衰减 + 上课时段峰值；published_at 滞后 0.5~72h
- 状态分布与发布时长关联（越旧越可能已完结）；REMOVED 帖配套 moderation_actions
- claims 与帖子状态联动（COMPLETED 帖→COMPLETED 申请+双向确认；HANDOVER 帖→唯一 WAITING_HANDOVER）
- 对话/线索文案贴合校园场景；申请人/线索人排除发布者本人（自认领约束）
"""
import random
import datetime as dt
import os
import sys

random.seed(20260929)

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "mock_data.sql")

N_USERS = 80
N_POSTS = 10000
TODAY = dt.datetime(2026, 9, 29, 20, 0, 0)  # 与当前会话日期一致

def esc(s: str) -> str:
    return s.replace("\\", "\\\\").replace("'", "''")

def q(s: str) -> str:
    return "'" + esc(s) + "'"

def fmt(dtobj) -> str:
    return dtobj.strftime("%Y-%m-%d %H:%M:%S")

# ---------------- 内容池 ----------------
CAMPUSES = [("南湖校区", 40), ("马房山校区", 35), ("余家头校区", 25)]
LOCATIONS = {
    "南湖校区": ["南湖图书馆", "南湖教学楼", "南湖食堂", "南苑宿舍楼下", "智园食堂", "南湖操场",
              "南湖体育馆", "南湖校区东门", "南湖快递站", "南湖林荫道", "南湖篮球场"],
    "马房山校区": ["东院图书馆", "西院图书馆", "博学广场", "鉴湖教学楼", "东院食堂", "学子超市",
               "马房山操场", "西院宿舍楼下", "鉴湖快递点", "东院篮球场", "马房山隧道口"],
    "余家头校区": ["余家头图书馆", "海运楼", "余家头食堂", "余区宿舍楼下", "水面训练馆",
               "余家头操场", "余区快递站", "余家头教学楼", "余区篮球场", "余区超市"],
}
CATEGORIES = [
    ("雨伞", 14), ("校园卡", 13), ("钥匙", 12), ("耳机", 11), ("水杯", 10),
    ("手机", 8), ("钱包", 7), ("充电宝", 7), ("书本教材", 6), ("证件", 4),
    ("手表饰品", 3), ("衣物", 3), ("笔记本电脑", 2), ("眼镜", 3), ("其他", 4),
]
ITEMS = {
    "雨伞": [("黑色长柄伞", ["伞柄有轻微磨损"]), ("折叠晴雨伞", ["伞面小碎花"]), ("透明自动伞", ["伞套还在"])],
    "校园卡": [("校园卡", ["卡面照片是本人"]), ("校园卡", ["卡套是蓝色的"]), ("校园卡与银行卡叠放", ["用皮夹包着"])],
    "钥匙": [("一串钥匙", ["挂着小熊挂件"]), ("宿舍钥匙", ["钥匙扣是金属圆环"]), ("电动车钥匙", ["黑色遥控器两把"])],
    "耳机": [("白色蓝牙耳机", ["充电盒外壳有划痕"]), ("黑色头戴式耳机", ["耳罩皮有些脱落"]), ("有线耳机", ["绕线器是绿色的"])],
    "水杯": [("白色保温杯", ["杯底有贴纸"]), ("透明塑料水杯", ["杯盖是浅蓝色"]), ("粉色吸管杯", ["吸管未拆封"])],
    "手机": [("黑色智能手机", ["手机壳是透明的"]), ("旧款安卓手机", ["屏幕左上角有裂纹"]), ("手机", ["锁屏壁纸是猫"])],
    "钱包": [("黑色短款钱包", ["内有少量现金"]), ("帆布卡包", ["印着卡通图案"]), ("棕色长款钱包", ["拉链头氧化"])],
    "充电宝": ["白色充电宝", "黑色 20000mAh 充电宝", "绿色迷你充电宝"],
    "书本教材": [("高数教材", ["扉页有笔记"]), ("考研英语真题", ["用荧光笔画过"]), ("一套专业课本", ["用文件袋装着"])],
    "证件": [("身份证", ["已交给保卫处登记"]), ("学生证", ["证件照清晰"]), ("驾驶证与证件夹", ["夹层有票据"])],
    "手表饰品": [("电子手表", ["表带是黑色的"]), ("银色手链", ["坠子是小星星"]), ("黑色皮带手表", ["表盘轻微划痕"])],
    "衣物": [("黑色卫衣", ["胸前有校徽印花"]), ("灰色围巾", ["流苏整齐"]), ("蓝色棒球帽", ["帽檐有洗旧痕迹"])],
    "笔记本电脑": [("银色笔记本电脑", ["贴有键盘膜"]), ("游戏本", ["A 面有贴纸"])],
    "眼镜": [("黑框眼镜", ["镜盒是棕色的"]), ("隐形眼镜护理液与镜盒", ["未开封"])],
    "其他": [("保温饭盒", ["蓝色双层"]), ("跳绳", ["手柄是紫色的"]), ("乒乓球拍", ["横拍，胶皮崭新"]), ("水壶", ["军绿色帆布套"])],
}
COLORS = ["黑色", "白色", "蓝色", "粉色", "银色", "灰色", "绿色", "米色"]

# 类别 → category_code（镜像 V3__post_category_code.sql / CategoryDictionary）。
# 直接写入，使 P4 同类目臂与 V4 事实链接回填（按 category_code 匹配）在模拟数据上生效。
CATEGORY_CODE = {
    "雨伞": "umbrella", "校园卡": "campus-card", "钥匙": "keys", "耳机": "earphones",
    "水杯": "cup", "手机": "phone", "钱包": "wallet", "充电宝": "powerbank",
    "书本教材": "books", "证件": "id-docs", "手表饰品": "watch-accessory", "衣物": "clothing",
    "笔记本电脑": "laptop", "眼镜": "glasses", "其他": "other",
}

NICK_FIRST = ["南湖", "马房山", "余家头", "鉴湖", "博学", "东院", "西院", "北苑", "南苑", "余区",
              "图书馆", "操场", "食堂", "水运", "航海", "材料", "汽院", "资环", "机电", "信息"]
NICK_SECOND = ["小张", "小王", "小李", "同学", "er", "打工人", "摸鱼侠", "路过的", "常驻居民",
               "小学生", "研究生", "爱吃火锅", "奶茶不加冰", "早起失败", "夜跑者", "路人甲",
               "在逃课代表", "早八人", "干饭王", "夜猫子", "跑步的", "学弱", "躺平选手", "在赶due"]

CLAIM_DESCS = [
    "特征吻合：挂件和颜色都对得上，具体特征可当面核验",
    "我丢的这款就是该型号，购买记录可以出示",
    "卡面/刻字特征我可以详细描述，请安排核实",
    "细节特征能完全对上，希望尽快交接",
]
MSG_APP = ["你好，我看到你发布的招领信息，那应该是我丢的东西",
           "特征我可以详细描述，包括磨损位置和贴纸",
           "请问周末下午方便交接吗，图书馆门口可以吗",
           "好的，我带校园卡核对身份，到时候联系",
           "麻烦了，东西对我挺重要的",
           "收到，那就约明天下午三点"]
MSG_PUB = ["你好，请先描述一下物品的明显特征，我核对后安排交接",
           "特征对得上，那我们约个时间地点交接",
           "可以，图书馆南门下午三点，请带上能证明身份的证件",
           "好，到时见，我这边会提前放在值班处登记",
           "东西一直保管得好好的，放心",
           "注意一下别公开唯一性细节，核验时当面说"]

def pick_weighted(pairs):
    total = sum(w for _, w in pairs)
    r = random.uniform(0, total)
    acc = 0
    for v, w in pairs:
        acc += w
        if r <= acc:
            return v
    return pairs[-1][0]

def gen_time():
    days_ago = int(random.expovariate(1 / 22.0))
    days_ago = min(days_ago, 118)
    hour_w = [(7,3),(8,9),(9,7),(10,6),(11,5),(12,6),(13,4),(14,7),(15,7),(16,6),(17,5),
              (18,6),(19,7),(20,8),(21,5),(22,3),(23,1)]
    total = sum(w for _, w in hour_w)
    r = random.uniform(0, total); acc = 0
    hour = 12
    for h, w in hour_w:
        acc += w
        if r <= acc:
            hour = h; break
    base = TODAY - dt.timedelta(days=days_ago)
    return base.replace(hour=hour, minute=random.randint(0, 59), second=random.randint(0, 59))

def part_of_day(h):
    return "上午" if h < 12 else ("下午" if h < 18 else "晚上")

def gen_post():
    ptype = "LOST" if random.random() < 0.47 else "FOUND"
    cat = pick_weighted(CATEGORIES)
    pool = ITEMS[cat]
    if isinstance(pool[0], tuple):
        item, feats = random.choice(pool)
        feat = random.choice(feats)
    else:
        item = random.choice(pool); feat = random.choice(["外观完好", "有使用痕迹", "略有磨损"])
    color = random.choice(COLORS)
    # 物品名已含颜色词则不再叠加颜色前缀，避免"绿色白色保温杯"式别扭标题
    color_prefix = "" if any(c in item for c in COLORS) else color
    campus = pick_weighted(CAMPUSES)
    loc = random.choice(LOCATIONS[campus])
    event_time = gen_time()
    lag_minutes = min(int(random.expovariate(1 / 900.0)) + 30, 72 * 60)
    published_at = event_time + dt.timedelta(minutes=lag_minutes)

    if ptype == "LOST":
        title = f"{random.choice(['丢失', '遗失'])}{color_prefix}{item}"
        desc = (f"{event_time.strftime('%m月%d日')}{part_of_day(event_time.hour)}"
                f"在{campus}{loc}遗失{color_prefix}{item}（{feat}）。对我很重要，"
                f"如有拾到请通过线索功能联系我，必有酬谢！")
    else:
        title = f"{random.choice(['捡到', '拾获'])}{color_prefix}{item}"
        desc = (f"{event_time.strftime('%m月%d日')}{part_of_day(event_time.hour)}"
                f"在{campus}{loc}捡到{color_prefix}{item}（{feat}），现保管在我处。"
                f"为保护失主隐私，未公开唯一性证明细节，认领时请通过申请功能提交特征说明核实。")

    age_days = (TODAY - published_at).days
    r = random.random()
    if ptype == "LOST":
        status = "ACTIVE" if r < 0.90 else ("COMPLETED" if r < 0.955 else ("WITHDRAWN" if r < 0.99 else "REMOVED"))
    else:
        status = ("ACTIVE" if r < 0.885 else ("COMPLETED" if r < 0.925 else
                 ("HANDOVER" if r < 0.95 else ("WITHDRAWN" if r < 0.985 else "REMOVED"))))
    if age_days <= 7 and status in ("HANDOVER", "COMPLETED") and random.random() < 0.7:
        status = "ACTIVE"
    return dict(type=ptype, title=title, category=cat, category_code=CATEGORY_CODE[cat],
                description=desc, campus=campus,
                location=loc, event_time=event_time, published_at=published_at,
                status=status, age_days=age_days)

# ---------------- 生成 SQL ----------------
lines = []
lines.append("-- 自动生成：1 万条真实感模拟数据（tests/performance/gen_mock_data.py）")
lines.append(f"-- 生成时间：{fmt(dt.datetime.now())}  种子：20260929")
lines.append("-- 约定：新用户/新帖按插入顺序获得连续自增 ID；@users_base/@posts_base 为各自起点。")
lines.append("SET NAMES utf8mb4;")
lines.append("SET autocommit=0;")
lines.append("START TRANSACTION;")
lines.append("")

# 1) 用户
nick_pool = set()
users_spec = []
for i in range(N_USERS):
    while True:
        nick = random.choice(NICK_FIRST) + random.choice(NICK_SECOND) + \
               (str(random.randint(0, 99)) if random.random() < 0.35 else "")
        if nick not in nick_pool and len(nick) <= 20:
            nick_pool.add(nick); break
    users_spec.append((nick, pick_weighted(CAMPUSES)))

lines.append(f"-- 1) 插入 {N_USERS} 个模拟用户")
for nick, campus in users_spec:
    lines.append(f"INSERT INTO users (nickname, campus, status, campus_verification_status, created_at, updated_at) "
                 f"VALUES ({q(nick)}, {q(campus)}, 'ACTIVE', 'UNVERIFIED', NOW(), NOW());")
lines.append(f"SET @users_base = (SELECT MAX(id) FROM users) - {N_USERS}; -- 新用户 id = @users_base + k, k=1..{N_USERS}")

# 2) 帖子
print("generating posts ...", file=sys.stderr)
posts = [gen_post() for _ in range(N_POSTS)]
lost_cnt = sum(1 for p in posts if p["type"] == "LOST")
print(f"posts: total={len(posts)} lost={lost_cnt} found={len(posts)-lost_cnt}", file=sys.stderr)

lines.append("")
lines.append(f"-- 2) 插入 {N_POSTS} 条发布（publisher_id = @users_base + 轮转序号；事件时间≠发布时间）")
def flush_post_batch(batch):
    if not batch:
        return
    lines.append("INSERT INTO posts (publisher_id, type, title, category, category_code, public_description, campus, event_location, event_time, published_at, status, version) VALUES")
    lines.append(",\n".join(batch) + ";")

batch = []
for n, p in enumerate(posts):
    k = (n % N_USERS) + 1
    batch.append(
        f"(@users_base + {k}, '{p['type']}', {q(p['title'])}, {q(p['category'])}, {q(p['category_code'])}, {q(p['description'])}, "
        f"{q(p['campus'])}, {q(p['location'])}, '{fmt(p['event_time'])}', '{fmt(p['published_at'])}', '{p['status']}', 0)"
    )
    if len(batch) == 500:
        flush_post_batch(batch); batch = []
flush_post_batch(batch)
lines.append(f"SET @posts_base = (SELECT MAX(id) FROM posts) - {N_POSTS}; -- 新帖 id = @posts_base + m, m=1..{N_POSTS}")

# 3) 治理
lines.append("")
lines.append("-- 3) REMOVED 帖配套 moderation_actions（管理员取第一条凭据）")
lines.append("""INSERT INTO moderation_actions (admin_id, target_type, target_id, action, reason, before_state, after_state, created_at)
SELECT (SELECT admin_user_id FROM admin_credentials ORDER BY admin_user_id LIMIT 1), 'POST', p.id, 'REMOVE',
       '公开内容含联系方式/商业推广信息，依据平台规则下架', 'ACTIVE', 'REMOVED',
       DATE_ADD(p.published_at, INTERVAL FLOOR(6 + RAND(p.id)*72) HOUR)
FROM posts p WHERE p.status = 'REMOVED' AND p.id > @posts_base;""")

# 4) claims 联动
lines.append("")
lines.append("-- 4.1 COMPLETED FOUND 帖 → COMPLETED 申请（申请人≠发布者，每帖 1 条）+ 双向确认")
lines.append("""INSERT INTO claims (post_id, applicant_id, description, status, reviewed_by, review_reason, reviewed_at, accepted_at, completed_at, created_at)
SELECT p.id,
       (SELECT a.id FROM users a WHERE a.id > @users_base AND a.id <> p.publisher_id ORDER BY RAND(p.id * 7) LIMIT 1),
       CONCAT('我丢的就是这个：', SUBSTRING(p.title, 3), '，特征能对上，可现场核验归属。'),
       'COMPLETED', p.publisher_id, '特征描述一致，接受申请',
       DATE_ADD(p.published_at, INTERVAL FLOOR(4 + RAND(p.id*3)*48) HOUR),
       DATE_ADD(p.published_at, INTERVAL FLOOR(4 + RAND(p.id*3)*48) HOUR),
       DATE_ADD(p.published_at, INTERVAL FLOOR(24 + RAND(p.id*5)*96) HOUR),
       DATE_ADD(p.published_at, INTERVAL FLOOR(2 + RAND(p.id*11)*20) HOUR)
FROM posts p
WHERE p.status='COMPLETED' AND p.type='FOUND' AND p.id > @posts_base;""")

lines.append("""INSERT INTO handover_confirmations (claim_id, confirmed_by, confirmed_at)
SELECT c.id, c.applicant_id, DATE_ADD(c.accepted_at, INTERVAL FLOOR(1+RAND(c.id)*20) HOUR) FROM claims c
WHERE c.status='COMPLETED' AND c.post_id > @posts_base
UNION ALL
SELECT c.id, p.publisher_id, DATE_ADD(c.accepted_at, INTERVAL FLOOR(2+RAND(c.id*3)*24) HOUR) FROM claims c
JOIN posts p ON p.id = c.post_id
WHERE c.status='COMPLETED' AND c.post_id > @posts_base;""")

lines.append("-- 4.2 HANDOVER FOUND 帖 → 唯一一条 WAITING_HANDOVER（生成列唯一索引兜底每帖一条）")
lines.append("""INSERT INTO claims (post_id, applicant_id, description, status, reviewed_by, review_reason, reviewed_at, accepted_at, created_at)
SELECT p.id,
       (SELECT a.id FROM users a WHERE a.id > @users_base AND a.id <> p.publisher_id ORDER BY RAND(p.id * 13) LIMIT 1),
       '和我丢的特征一致（颜色/挂件/磨损位置都能对上），可以现场核验，恳请安排交接。',
       'WAITING_HANDOVER', p.publisher_id, '描述匹配，进入交接',
       DATE_ADD(p.published_at, INTERVAL FLOOR(3 + RAND(p.id*17)*30) HOUR),
       DATE_ADD(p.published_at, INTERVAL FLOOR(3 + RAND(p.id*17)*30) HOUR),
       DATE_ADD(p.published_at, INTERVAL FLOOR(1 + RAND(p.id*19)*16) HOUR)
FROM posts p WHERE p.status='HANDOVER' AND p.type='FOUND' AND p.id > @posts_base;""")

lines.append("-- 4.3 ACTIVE FOUND 帖 → 0~2 条 PENDING 申请（同帖申请人互不相同、排除发布者）")
lines.append("""INSERT INTO claims (post_id, applicant_id, description, status, created_at)
SELECT t.post_id, t.applicant_id,
       CONCAT('特征吻合：', ELT(1 + FLOOR(RAND(t.post_id*100 + t.applicant_id)*4),
         '挂件和颜色都对得上，具体特征可当面核验',
         '我丢的这款就是该型号，购买记录可以出示',
         '卡面/刻字特征我可以详细描述，请安排核实',
         '细节特征能完全对上，希望尽快交接'), '。'),
       'PENDING', DATE_ADD(t.pub, INTERVAL FLOOR(1 + RAND(t.post_id*3 + t.applicant_id)*36) HOUR)
FROM (
  SELECT p.id AS post_id, p.published_at AS pub, u.id AS applicant_id,
         ROW_NUMBER() OVER (PARTITION BY p.id ORDER BY u.id) AS rn
  FROM posts p JOIN users u
    ON u.id > @users_base AND u.id <> p.publisher_id AND (u.id + p.id) % 7 <> 0
   AND RAND(p.id * 31 + u.id) < 0.02
  WHERE p.status='ACTIVE' AND p.type='FOUND' AND p.id > @posts_base
) t WHERE t.rn <= 1 + FLOOR(RAND(t.post_id * 3) * 2);""")

lines.append("-- 4.4 ACTIVE FOUND 帖上的历史 REJECTED 申请")
lines.append("""INSERT INTO claims (post_id, applicant_id, description, status, reviewed_by, review_reason, reviewed_at, created_at)
SELECT p.id,
       (SELECT a.id FROM users a WHERE a.id > @users_base AND a.id <> p.publisher_id ORDER BY RAND(p.id * 41) LIMIT 1),
       '描述比较笼统，特征无法核实。', 'REJECTED', p.publisher_id,
       '特征描述不充分，无法确认归属',
       DATE_ADD(p.published_at, INTERVAL FLOOR(5 + RAND(p.id*29)*40) HOUR),
       DATE_ADD(p.published_at, INTERVAL FLOOR(2 + RAND(p.id*37)*24) HOUR)
FROM posts p WHERE p.status='ACTIVE' AND p.type='FOUND' AND p.id > @posts_base AND RAND(p.id*43) < 0.25;""")

lines.append("-- 4.5 争议：抽样 WAITING_HANDOVER 申请，一条 OPEN、一条 RESOLVED(CONTINUE)")
lines.append("""INSERT INTO disputes (claim_id, raised_by, reason, description, status, created_at)
SELECT c.id, c.applicant_id, 'WRONG_ITEM', '交接时发现与我的物品存在差异，申请管理员介入核实。', 'OPEN',
       DATE_ADD(c.accepted_at, INTERVAL 5 HOUR)
FROM claims c WHERE c.status='WAITING_HANDOVER' AND c.post_id > @posts_base
ORDER BY c.id LIMIT 1;""")
lines.append("""INSERT INTO disputes (claim_id, raised_by, reason, description, status, assigned_admin_id, resolution_type, resolution_note, resolved_at, created_at)
SELECT c.id, p.publisher_id, 'OWNERSHIP_DOUBT', '申请人对物品归属提出异议，请管理员复核双方证据。', 'RESOLVED',
       (SELECT admin_user_id FROM admin_credentials ORDER BY admin_user_id LIMIT 1), 'CONTINUE',
       '双方证据比对后认定描述一致，维持交接继续。', DATE_ADD(c.accepted_at, INTERVAL 30 HOUR),
       DATE_ADD(c.accepted_at, INTERVAL 8 HOUR)
FROM claims c JOIN posts p ON p.id = c.post_id
WHERE c.status='WAITING_HANDOVER' AND c.post_id > @posts_base
ORDER BY c.id DESC LIMIT 1;""")

# 4.6 事实链接回填（V4）
lines.append("")
lines.append("-- 4.6 事实链接回填（V4）：约 40% 的 COMPLETED 认领，选认领人名下【同类目且事件时间最接近】的")
lines.append("--     ACTIVE LOST 帖，闭环为 COMPLETED 并写 resolved_by_claim_id + closed_at(=认领完成时间)。")
lines.append("--     用临时表落地候选，避免 MySQL 更新目标表被子查询引用（err 1093）；")
lines.append("--     双层 ROW_NUMBER 保证每个 claim 取一条、每个 LOST 帖只被一个 claim 关联。")
lines.append("""DROP TEMPORARY TABLE IF EXISTS tmp_resolved_link;
CREATE TEMPORARY TABLE tmp_resolved_link AS
SELECT claim_id, lost_id, completed_at FROM (
  SELECT claim_id, lost_id, completed_at,
         ROW_NUMBER() OVER (PARTITION BY lost_id ORDER BY claim_id) AS lr
  FROM (
    SELECT c.id AS claim_id, c.completed_at AS completed_at, l.id AS lost_id,
           ROW_NUMBER() OVER (PARTITION BY c.id ORDER BY
             ABS(TIMESTAMPDIFF(HOUR, l.event_time, f.event_time))) AS rn
    FROM claims c
    JOIN posts f ON f.id = c.post_id
    JOIN posts l ON l.publisher_id = c.applicant_id AND l.type='LOST' AND l.status='ACTIVE'
                AND l.category_code IS NOT NULL AND l.category_code = f.category_code
                AND f.event_time BETWEEN l.event_time - INTERVAL 24 HOUR
                                     AND l.event_time + INTERVAL 30 DAY
    WHERE c.status='COMPLETED' AND c.post_id > @posts_base AND RAND(c.id * 97) < 0.40
  ) per_claim WHERE rn = 1
) per_lost WHERE lr = 1;
UPDATE posts l JOIN tmp_resolved_link t ON l.id = t.lost_id
SET l.status='COMPLETED', l.closed_at = t.completed_at, l.resolved_by_claim_id = t.claim_id
WHERE l.status='ACTIVE';
DROP TEMPORARY TABLE IF EXISTS tmp_resolved_link;""")

# 5) 留言
lines.append("")
lines.append("-- 5) 申请内留言：WAITING_HANDOVER/COMPLETED 的双方各 1 条（对话起始）")
lines.append(f"""INSERT INTO claim_messages (claim_id, sender_id, body, read_at, created_at)
SELECT c.id, c.applicant_id, {q(MSG_APP[0])}, NULL,
       DATE_ADD(c.created_at, INTERVAL FLOOR(RAND(c.id*7)*10) HOUR)
FROM claims c WHERE c.status IN ('WAITING_HANDOVER','COMPLETED') AND c.post_id > @posts_base;
INSERT INTO claim_messages (claim_id, sender_id, body, read_at, created_at)
SELECT c.id, p.publisher_id, {q(MSG_PUB[0])}, NULL,
       DATE_ADD(c.created_at, INTERVAL FLOOR(1 + RAND(c.id*13)*10) HOUR)
FROM claims c JOIN posts p ON p.id = c.post_id
WHERE c.status IN ('WAITING_HANDOVER','COMPLETED') AND c.post_id > @posts_base;
INSERT INTO claim_messages (claim_id, sender_id, body, read_at, created_at)
SELECT c.id, c.applicant_id,
       ELT(1 + FLOOR(RAND(c.id*17)*5), {", ".join(q(m) for m in MSG_APP[1:])}), NULL,
       DATE_ADD(c.created_at, INTERVAL FLOOR(2 + RAND(c.id*19)*12) HOUR)
FROM claims c WHERE c.status IN ('WAITING_HANDOVER','COMPLETED') AND c.post_id > @posts_base AND RAND(c.id*23) < 0.8;""")

# 6) 线索
lines.append("")
lines.append("-- 6) 寻物线索：ACTIVE LOST 帖抽样（reporter≠发布者，状态四档分布）")
lines.append("""INSERT INTO lost_leads (lost_post_id, reporter_id, body, status, created_at, updated_at)
SELECT t.post_id, t.reporter_id, t.body, t.st, t.created_at,
       CASE WHEN t.st <> 'SUBMITTED' THEN DATE_ADD(t.created_at, INTERVAL FLOOR(RAND(t.post_id + t.reporter_id)*48) HOUR) ELSE t.created_at END
FROM (
  SELECT p.id AS post_id, u.id AS reporter_id,
         ROW_NUMBER() OVER (PARTITION BY p.id ORDER BY u.id) AS rn,
         ELT(1 + FLOOR(RAND(p.id*53 + u.id)*7),
             '我看到前天下午在南湖食堂好像有人捡到过一个类似的，可以去保卫处问问登记',
             '周三晚上我在图书馆三楼附近见过一个类似的，当时没拿，你可以去那附近找找',
             '保洁阿姨在东院食堂清扫的时候收过类似的东西，建议去楼管那里问问',
             '我室友好像捡到一个类似的，我让他来提交申请确认',
             '这个和快递站无人认领的那个很像，你去看看是不是',
             '路过博学广场时看到台阶上有个类似的东西，被值班同学收走了',
             '好像和我同学丢的很像，让他直接来联系你确认') AS body,
         CASE WHEN RAND(p.id*59 + u.id) < 0.55 THEN 'SUBMITTED'
              WHEN RAND(p.id*61 + u.id) < 0.55 THEN 'VIEWED'
              WHEN RAND(p.id*67 + u.id) < 0.73 THEN 'HELPFUL' ELSE 'CLOSED' END AS st,
         DATE_ADD(p.published_at, INTERVAL FLOOR(2 + RAND(p.id*71)*60) HOUR) AS created_at
  FROM posts p JOIN users u
    ON u.id > @users_base AND u.id <> p.publisher_id AND (u.id * 3 + p.id) % 5 <> 0
   AND RAND(p.id * 7 + u.id * 13) < 0.016
  WHERE p.status='ACTIVE' AND p.type='LOST' AND p.id > @posts_base
) t WHERE t.rn = 1;""")

lines.append("")
lines.append("COMMIT;")
lines.append("")
lines.append("-- ===== 导入后核对 =====")
lines.append("SELECT 'users' t, COUNT(*) c FROM users WHERE id > @users_base")
lines.append("UNION ALL SELECT 'posts', COUNT(*) FROM posts WHERE id > @posts_base")
lines.append("UNION ALL SELECT 'posts_ACTIVE', COUNT(*) FROM posts WHERE id > @posts_base AND status='ACTIVE'")
lines.append("UNION ALL SELECT 'resolved pairs', COUNT(*) FROM posts WHERE id > @posts_base AND resolved_by_claim_id IS NOT NULL")
lines.append("UNION ALL SELECT 'claims', COUNT(*) FROM claims WHERE post_id > @posts_base")
lines.append("UNION ALL SELECT 'handover_confirmations', COUNT(*) FROM handover_confirmations WHERE claim_id IN (SELECT id FROM claims WHERE post_id > @posts_base)")
lines.append("UNION ALL SELECT 'claim_messages', COUNT(*) FROM claim_messages WHERE claim_id IN (SELECT id FROM claims WHERE post_id > @posts_base)")
lines.append("UNION ALL SELECT 'lost_leads', COUNT(*) FROM lost_leads WHERE lost_post_id > @posts_base")
lines.append("UNION ALL SELECT 'disputes', COUNT(*) FROM disputes WHERE claim_id IN (SELECT id FROM claims WHERE post_id > @posts_base)")
lines.append("UNION ALL SELECT 'moderation_actions', COUNT(*) FROM moderation_actions WHERE target_id > @posts_base AND target_type='POST';")

with open(OUT, "w", encoding="utf-8") as f:
    f.write("\n".join(lines))
print(f"written: {OUT} ({len(lines)} lines)", file=sys.stderr)
