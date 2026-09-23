# User Knowledge State Specification v1

## 1. Trách nhiệm

Module này biến chuỗi evidence thành estimate hiện tại về kiến thức của user. State là projection có thể dựng lại; evidence ledger mới là lịch sử bất biến.

Không đồng nhất:

- `mastery`: mức năng lực được hệ thống ước lượng.
- `confidence`: độ chắc chắn của estimate do lượng/chất evidence.
- `self_confidence`: mức user tự tin khi trả lời.

## 2. Data model

### 2.1 KnowledgeEvidence ledger

Lưu `id`, `user_id`, `knowledge_node_id`, `dimension`, `score`, `reliability`, `source_type`, `source_id`, `observed_at`, `policy_version`.

- Append-only.
- Unique `(source_type, source_id, knowledge_node_id, dimension)` để chống trùng.
- Correction tạo reversal/replacement record; không sửa lịch sử âm thầm.

### 2.2 UserKnowledge

| Field | Rule |
|---|---|
| `user_id`, `knowledge_node_id` | Unique pair |
| `recognition_score` | `[0,1]` |
| `understanding_score` | `[0,1]` |
| `recall_score` | `[0,1]` |
| `application_score` | `[0,1]` |
| `mastery_score` | Derived `[0,1]` |
| `confidence` | Derived `[0,1]` |
| `evidence_count` | Non-negative |
| `attempt_count`, `correct_count` | Metrics, không thay thế evidence |
| `last_assessed_at`, `last_practiced_at` | Nullable |
| `next_review_at` | Nullable derived snapshot from `review`; progress does not advance intervals |
| `status` | `UNKNOWN`, `LEARNING`, `PROVISIONAL`, `MASTERED`, `REVIEW_DUE` |
| `state_version` | Optimistic locking |
| `policy_version` | Công thức đã dùng |

## 3. State update v1

Với evidence mới `e` và điểm dimension cũ `s`, policy `knowledge-state-v1` áp dụng
evidence tại chính observation point bất biến của nó:

```text
effectiveReliability = clamp(e.reliability, 0, 1)
alpha = clamp(MIN_ALPHA + effectiveReliability × ALPHA_RANGE, MIN_ALPHA, MAX_ALPHA)
newScore = clamp(s + alpha × (e.score - s), 0, 1)
```

Cấu hình mặc định:

```yaml
MIN_ALPHA: 0.10
ALPHA_RANGE: 0.35
MAX_ALPHA: 0.45
INITIAL_DIMENSION_SCORE: 0.0
```

Elapsed time không thay đổi acquisition score đã lưu. Decay chỉ được áp dụng khi tạo
effective snapshot tại một `projectionAsOf` rõ ràng. Quy tắc này bảo đảm incremental
ingest và replay không diễn giải lại alpha lịch sử theo thời điểm chạy lại.

Evidence được áp dụng theo `(observed_at ASC, source_event_id ASC)`. Với cùng ledger,
policy version và `projectionAsOf`, live projection và replay phải tạo cùng kết quả.
Formula v1 ưu tiên dễ giải thích; không tuyên bố đây là mô hình thống kê tối ưu.

Calculation dùng `BigDecimal` với precision/rounding được policy chỉ định; persisted
score dùng `DECIMAL(5,4)`. Replay drift tolerance v1 là `0.0001` và thuộc policy
version.

## 4. Mastery v1

```text
mastery =
    recognition   × 0.15
  + understanding × 0.25
  + recall        × 0.25
  + application   × 0.35
```

Invariant: trọng số không âm và tổng bằng 1. Goal khác có thể override weights bằng policy riêng, nhưng decision phải lưu policy version.

Nếu một dimension chưa có evidence, điểm mặc định là 0 và confidence thấp; không tự suy diễn từ dimension khác.

## 5. Estimate confidence v1

Confidence tăng theo số lượng, độ đa dạng và chất lượng evidence:

```text
volume = 1 - exp(-reliableEvidenceSum / 3)
coverage = dimensionsWithReliableEvidence / 4
recency = exp(-daysSinceLastEvidence / 60)
confidence = clamp(0.5 × volume + 0.3 × coverage + 0.2 × recency, 0, 1)
```

`reliableEvidenceSum` là tổng reliability sau decay trong cửa sổ policy. Evidence có reliability dưới `0.3` không tính vào coverage.

## 6. Status transition

Status được xét theo thứ tự ưu tiên dưới đây để mọi boundary chỉ khớp một kết quả.
`mastery` trong acquisition rules là stored mastery vừa projection; retention rule
dùng effective mastery tại `projectionAsOf`.

| Status | Điều kiện v1 |
|---|---|
| `UNKNOWN` | Chưa có evidence |
| `REVIEW_DUE` | Đã từng đạt `MASTERED` và (`next_review_at <= projectionAsOf` hoặc effective mastery < 0.75) |
| `MASTERED` | stored mastery ≥ 0.80, confidence ≥ 0.60 và review chưa due |
| `PROVISIONAL` | stored mastery ≥ 0.80 nhưng confidence < 0.60 |
| `LEARNING` | Mọi trường hợp còn lại có evidence, gồm `0.75 <= mastery < 0.80` |

Transition được tính lại sau mỗi evidence và thay đổi review snapshot. Ngưỡng nằm
trong policy. Acquisition threshold `0.80` và retention threshold `0.75` tạo hysteresis
có chủ đích; concept chưa từng mastered không trở thành `REVIEW_DUE` chỉ vì mastery
thấp.

## 7. Forgetting và review

Không ghi đè raw state mỗi ngày. Khi đọc cho planner, tính `effective_score` theo decay:

```text
effectiveDimension = dimensionScore × exp(-lambda × daysSinceRelevantEvidence)
```

Lambda theo dimension; recall thường decay nhanh hơn application. Planner sử dụng effective mastery, UI có thể hiển thị stored mastery kèm trạng thái review.

`knowledge-state-v1` biểu diễn lambda bằng half-life dễ audit: recognition 120 ngày,
understanding 150 ngày, recall 45 ngày và application 180 ngày. Confidence dùng cửa sổ
365 ngày; evidence có effective reliability dưới `0.30` không tính coverage.

Module `review` sở hữu lịch và việc tăng/giảm interval. Lịch mặc định sau successful
review: `1, 3, 7, 14, 30, 60` ngày. Sai hoặc score < 0.6 đưa interval về 1 ngày; score
0.6–0.79 rút một bậc (không thấp hơn bậc đầu); score ≥ 0.8 tăng một bậc. Progress nhận review snapshot qua
public contract và không tự cập nhật schedule.

## 8. Misconception state

Lưu `user_misconception(user_id, code, knowledge_node_id, severity, confidence, first_seen_at, last_seen_at, resolved_at)`.

- Evidence lặp lại cùng misconception tăng confidence/severity theo policy.
- Một câu đúng không tự xóa misconception.
- Cần verification evidence đúng dimension để đánh dấu resolved.

`misconception-v1` tích lũy confidence theo
`1 - (1 - oldConfidence) × (1 - evidenceReliability)`. Hai verification evidence sau
lần quan sát cuối, cùng node/dimension, score `>= 0.80`, reliability `>= 0.70` và không
lặp code sẽ đánh dấu resolved. Chỉ code trong taxonomy versioned mới được chấp nhận.

## 9. Consistency và concurrency

- Consumer idempotent theo evidence ID.
- Update dùng optimistic locking và retry có giới hạn.
- Ghi ledger trước, cập nhật projection sau qua transaction/outbox.
- Có job `rebuildUserKnowledge(userId, nodeId, policyVersion, projectionAsOf)` để replay.
- Nếu rebuild khác projection quá tolerance, phát hiện drift và audit; không âm thầm sửa hàng loạt.

## 10. API tối thiểu

```http
GET /api/v1/knowledge/me
GET /api/v1/knowledge/me/{nodeId}
GET /api/v1/knowledge/me/{nodeId}/evidence
POST /api/v1/internal/knowledge-state/rebuild
```

Evidence detail có thể ẩn evaluator internals nhưng phải cung cấp source, thời gian, dimension và kết quả cho audit phù hợp quyền.

## 11. Acceptance criteria

- Hai lần ingest cùng evidence ID chỉ cập nhật state một lần.
- Evidence reliability cao dịch chuyển score mạnh hơn reliability thấp.
- Mastery luôn bằng weighted dimensions của đúng policy version.
- Mastery cao nhưng evidence ít tạo `PROVISIONAL`, không `MASTERED`.
- Replay cùng ledger/policy/`projectionAsOf` tạo cùng state trong tolerance `0.0001`.
- User không đọc được evidence/state của user khác.
- Review due được tính từ server time và timezone-independent.
