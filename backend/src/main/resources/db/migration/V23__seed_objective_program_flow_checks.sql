-- Project-authored, bounded conceptual checks for one graph root. Neither
-- question measures unaided recall or code application.
INSERT INTO resources(id,resource_key,provider,source_type,license_code,created_at) VALUES
(10007,'counter-trace-check','SkillPath','PROJECT_AUTHORED','SKILLPATH-ORIGINAL',UTC_TIMESTAMP(6)),
(10008,'branch-trace-check','SkillPath','PROJECT_AUTHORED','SKILLPATH-ORIGINAL',UTC_TIMESTAMP(6));

INSERT INTO resource_versions(id,resource_id,version_number,resource_type,title,body,content_ref,section_key,difficulty,estimated_minutes,status,created_at) VALUES
(11007,10007,1,'INTERNAL','Trace a counter before checking','Trace the value of a counter after each loop iteration. Separate the loop condition from the final branch condition. Write your reasoning before opening the objective check.','skillpath://counter-trace-check/1','counter-trace',1,12,'ACTIVE',UTC_TIMESTAMP(6)),
(11008,10008,1,'INTERNAL','Trace a branch before checking','Read each assignment in order and decide which branch executes. Draw a two-column table for variable values and conditions, then submit the objective check.','skillpath://branch-trace-check/1','branch-trace',1,12,'ACTIVE',UTC_TIMESTAMP(6));

INSERT INTO resource_version_translations(resource_version_id,locale,title,body) VALUES
(11007,'vi-VN','Lần theo biến đếm trước khi kiểm tra','Ghi giá trị của biến đếm sau từng vòng lặp. Phân biệt điều kiện lặp với điều kiện rẽ nhánh cuối. Viết cách suy luận trước khi mở câu kiểm tra khách quan.'),
(11008,'vi-VN','Lần theo nhánh trước khi kiểm tra','Đọc từng phép gán theo thứ tự và xác định nhánh được chạy. Lập bảng hai cột cho giá trị biến và điều kiện, rồi nộp câu kiểm tra khách quan.');

INSERT INTO knowledge_resources(resource_version_id,graph_version_id,knowledge_node_id,section_key,purpose,estimated_minutes) VALUES
(11007,1,1001,'counter-trace','PRACTICE',12),
(11008,1,1001,'branch-trace','PRACTICE',12);

INSERT INTO task_templates(id,template_key,created_at) VALUES
(12007,'counter-trace-objective',UTC_TIMESTAMP(6)),
(12008,'branch-trace-objective',UTC_TIMESTAMP(6));

INSERT INTO task_template_versions(id,template_id,version_number,graph_version_id,resource_version_id,title,instructions,checklist,activity_type,evaluation_mode,difficulty,estimated_minutes,min_minutes,max_minutes,variant_group_key,status,content_source,created_at) VALUES
(13007,12007,1,1,11007,'Practice: trace a counter','Complete the short trace, then answer one objective question. Completing the check records evidence, not mastery.',JSON_ARRAY(JSON_OBJECT('id','trace','label','I wrote the counter trace')),'PRACTICE','OBJECTIVE',1,12,12,12,'programming-flow-check','ACTIVE','CURATED',UTC_TIMESTAMP(6)),
(13008,12008,1,1,11008,'Practice: trace a branch','Complete the short branch trace, then answer one objective question. Completing the check records evidence, not mastery.',JSON_ARRAY(JSON_OBJECT('id','trace','label','I wrote the branch trace')),'PRACTICE','OBJECTIVE',1,12,12,12,'programming-flow-check','ACTIVE','CURATED',UTC_TIMESTAMP(6));

INSERT INTO task_template_translations(task_template_version_id,locale,title,instructions,checklist) VALUES
(13007,'vi-VN','Thực hành: lần theo biến đếm','Hoàn thành bài lần theo ngắn rồi trả lời một câu khách quan. Câu kiểm tra tạo bằng chứng, không tự xác nhận thành thạo.',JSON_ARRAY(JSON_OBJECT('id','trace','label','Tôi đã ghi bảng giá trị biến đếm'))),
(13008,'vi-VN','Thực hành: lần theo nhánh','Hoàn thành bài lần theo nhánh rồi trả lời một câu khách quan. Câu kiểm tra tạo bằng chứng, không tự xác nhận thành thạo.',JSON_ARRAY(JSON_OBJECT('id','trace','label','Tôi đã ghi bảng các nhánh')));

INSERT INTO task_template_knowledge(task_template_version_id,graph_version_id,knowledge_node_id,mapping_role,dimension,weight) VALUES
(13007,1,1001,'PRIMARY','UNDERSTANDING',1.0000),
(13008,1,1001,'PRIMARY','UNDERSTANDING',1.0000);

INSERT INTO questions(id,question_key,created_at,updated_at) VALUES
(3009,'task-check-counter-trace',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
(3010,'task-check-branch-trace',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6));

INSERT INTO question_versions(id,question_id,version_number,type,prompt,difficulty,estimated_seconds,scoring_strategy,options,answer_key,rubric,status,source,created_at,updated_at) VALUES
(3109,3009,1,'MULTIPLE_CHOICE','Start count at 0 and add 2 on each of three iterations. Which statements are true after the loop? Select all that apply.',1,60,'EXACT',
 JSON_ARRAY(JSON_OBJECT('id','count-six','label','count is 6'),JSON_OBJECT('id','iterations-three','label','The loop ran three times'),JSON_OBJECT('id','count-four','label','count is 4'),JSON_OBJECT('id','iterations-four','label','The loop ran four times')),
 JSON_OBJECT('correctOptionIds',JSON_ARRAY('count-six','iterations-three')),NULL,'ACTIVE','HUMAN',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
(3110,3010,1,'SINGLE_CHOICE','Set x to 1. If x is greater than 2, set x to 5; otherwise add 3 to x. What is x afterward?',1,45,'EXACT',
 JSON_ARRAY(JSON_OBJECT('id','four','label','4'),JSON_OBJECT('id','five','label','5'),JSON_OBJECT('id','one','label','1'),JSON_OBJECT('id','three','label','3')),
 JSON_OBJECT('correctOptionIds',JSON_ARRAY('four')),NULL,'ACTIVE','HUMAN',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6));

INSERT INTO question_knowledge(question_version_id,graph_version_id,knowledge_node_id,dimension,weight,max_evidence_strength,created_at) VALUES
(3109,1,1001,'UNDERSTANDING',1.0000,0.5500,UTC_TIMESTAMP(6)),
(3110,1,1001,'UNDERSTANDING',1.0000,0.4500,UTC_TIMESTAMP(6));

INSERT INTO question_version_translations(question_version_id,locale,prompt,options,created_at) VALUES
(3109,'vi-VN','Bắt đầu count bằng 0 và cộng 2 trong mỗi lượt của ba vòng lặp. Sau vòng lặp, nhận định nào đúng? Chọn tất cả đáp án đúng.',
 JSON_ARRAY(JSON_OBJECT('id','count-six','label','count bằng 6'),JSON_OBJECT('id','iterations-three','label','Vòng lặp chạy ba lần'),JSON_OBJECT('id','count-four','label','count bằng 4'),JSON_OBJECT('id','iterations-four','label','Vòng lặp chạy bốn lần')),UTC_TIMESTAMP(6)),
(3110,'vi-VN','Đặt x bằng 1. Nếu x lớn hơn 2 thì đặt x bằng 5; nếu không thì cộng 3 vào x. Sau đó x bằng bao nhiêu?',
 JSON_ARRAY(JSON_OBJECT('id','four','label','4'),JSON_OBJECT('id','five','label','5'),JSON_OBJECT('id','one','label','1'),JSON_OBJECT('id','three','label','3')),UTC_TIMESTAMP(6));

INSERT INTO task_check_definitions(task_template_version_id,graph_version_id,question_version_id,evaluator_version,status,created_at) VALUES
(13007,1,3109,'task-check-objective-v1','ACTIVE',UTC_TIMESTAMP(6)),
(13008,1,3110,'task-check-objective-v1','ACTIVE',UTC_TIMESTAMP(6));
