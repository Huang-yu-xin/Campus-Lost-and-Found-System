# 事实链接对评估

- 生成时间（UTC）：2026-10-01T10:16:32
- 事实链接对数：29（来自 posts.resolved_by_claim_id）
- 候选阶段召回（点时重建，closed_at 版）：100%（29/29）
- Hit@1 / @5 / @20：10% / 10% / 24%

## 逐对明细
- 42431->45219 rank=缺席 recalled=是 score=0.47 t=2026-07-30T08:41:39
- 46111->43069 rank=缺席 recalled=是 score=0.41 t=2026-08-20T20:05:20
- 43471->41827 rank=缺席 recalled=是 score=0.41 t=2026-08-24T22:11:25
- 42991->43648 rank=缺席 recalled=是 score=0.53 t=2026-08-21T06:14:44
- 41711->45198 rank=缺席 recalled=是 score=0.57 t=2026-08-17T15:08:12
- 42671->41210 rank=缺席 recalled=是 score=0.41 t=2026-09-15T23:10:01
- 44031->48293 rank=缺席 recalled=是 score=0.48 t=2026-09-10T14:26:31
- 44671->48925 rank=缺席 recalled=是 score=0.67 t=2026-08-31T03:57:01
- 41471->40677 rank=缺席 recalled=是 score=0.48 t=2026-09-18T17:00:48
- 47311->45751 rank=缺席 recalled=是 score=0.71 t=2026-09-06T18:02:57
- 40351->44562 rank=16 recalled=是 score=0.71 t=2026-09-05T09:47:29
- 41551->41829 rank=缺席 recalled=是 score=0.66 t=2026-09-14T21:50:02
- 47071->45422 rank=缺席 recalled=是 score=0.69 t=2026-09-10T09:07:20
- 46911->46672 rank=缺席 recalled=是 score=0.63 t=2026-09-16T17:57:10
- 40591->47823 rank=8 recalled=是 score=0.76 t=2026-09-14T11:56:50
- 41951->48809 rank=缺席 recalled=是 score=0.67 t=2026-09-13T04:38:29
- 44831->49860 rank=缺席 recalled=是 score=0.69 t=2026-09-17T04:17:08
- 47471->49411 rank=缺席 recalled=是 score=0.70 t=2026-09-14T13:03:04
- 49391->49190 rank=14 recalled=是 score=0.72 t=2026-09-20T10:51:50
- 40991->40476 rank=缺席 recalled=是 score=0.71 t=2026-09-21T15:41:10
- 40191->49314 rank=缺席 recalled=是 score=0.65 t=2026-09-27T23:29:39
- 42751->46182 rank=缺席 recalled=是 score=0.71 t=2026-09-26T12:56:19
- 43711->47042 rank=缺席 recalled=是 score=0.68 t=2026-09-29T19:00
- 45151->43528 rank=缺席 recalled=是 score=0.69 t=2026-09-27T10:49:42
- 49711->43778 rank=6 recalled=是 score=0.73 t=2026-09-29T19:00
- 50032->50031 rank=1 recalled=是 score=0.95 t=2026-10-01T14:33:04
- 50035->50034 rank=1 recalled=是 score=0.95 t=2026-10-01T15:04:50
- 50038->50037 rank=1 recalled=是 score=0.95 t=2026-10-01T10:13:31
- 44271->43180 rank=缺席 recalled=是 score=0.71 t=2026-09-29T19:00

> 正样本来自失主确认的 resolved_by_claim_id 事实链接（无标签噪声）；候选池按 t=min(lost.closed_at, found.closed_at) 点时重建，分数由生产 MatchScorer 计算。
