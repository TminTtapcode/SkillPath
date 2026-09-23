INSERT INTO resources(id,resource_key,provider,source_type,license_code,created_at) VALUES
(10001,'programming-flow-learn','SkillPath','PROJECT_AUTHORED','SKILLPATH-ORIGINAL',UTC_TIMESTAMP(6)),
(10002,'programming-flow-practice','SkillPath','PROJECT_AUTHORED','SKILLPATH-ORIGINAL',UTC_TIMESTAMP(6)),
(10003,'programming-flow-recall','SkillPath','PROJECT_AUTHORED','SKILLPATH-ORIGINAL',UTC_TIMESTAMP(6));

INSERT INTO resource_versions(id,resource_id,version_number,resource_type,title,body,content_ref,section_key,difficulty,estimated_minutes,status,created_at) VALUES
(11001,10001,1,'INTERNAL','Trace a small program','A program runs statements in order. A variable holds a value. A condition chooses a branch, and a loop repeats a block while its condition holds. Trace values after each statement instead of guessing from the final line.','skillpath://programming-flow-learn/1','trace-variables',1,10,'ACTIVE',UTC_TIMESTAMP(6)),
(11002,10002,1,'INTERNAL','Practice: trace a counter','Set count to 0. Repeat three times: add 2 to count. After the loop, if count is greater than 5, set result to count; otherwise set result to 0. Trace count after every repetition and determine result. Check your work by running a tiny program or writing a table.','skillpath://programming-flow-practice/1','counter-exercise',1,15,'ACTIVE',UTC_TIMESTAMP(6)),
(11003,10003,1,'INTERNAL','Recall without notes','Close the prior explanation. Explain from memory how statement order, a variable, a condition, and a loop affected the counter exercise. Then reopen your notes and identify anything you missed.','skillpath://programming-flow-recall/1','recall-prompt',1,5,'ACTIVE',UTC_TIMESTAMP(6));

INSERT INTO resource_version_translations(resource_version_id,locale,title,body) VALUES
(11001,'vi-VN','Lần theo một chương trình nhỏ','Chương trình chạy các câu lệnh theo thứ tự. Biến lưu một giá trị. Điều kiện chọn một nhánh, còn vòng lặp lặp lại một khối khi điều kiện còn đúng. Hãy ghi giá trị sau từng câu lệnh thay vì đoán từ dòng cuối.'),
(11002,'vi-VN','Thực hành: lần theo biến đếm','Đặt count bằng 0. Lặp ba lần: cộng 2 vào count. Sau vòng lặp, nếu count lớn hơn 5 thì đặt result bằng count; nếu không thì đặt result bằng 0. Ghi count sau mỗi lần lặp và xác định result. Kiểm tra bằng chương trình nhỏ hoặc bảng giá trị.'),
(11003,'vi-VN','Nhớ lại không xem ghi chú','Đóng phần giải thích trước đó. Tự giải thích thứ tự câu lệnh, biến, điều kiện và vòng lặp đã ảnh hưởng bài biến đếm như thế nào. Sau đó mở lại ghi chú và tìm những ý mình đã bỏ sót.');

INSERT INTO knowledge_resources(resource_version_id,graph_version_id,knowledge_node_id,section_key,purpose,estimated_minutes) VALUES
(11001,1,1001,'trace-variables','FOUNDATION',10),
(11002,1,1001,'counter-exercise','PRACTICE',15),
(11003,1,1001,'recall-prompt','RECALL',5);

INSERT INTO task_templates(id,template_key,created_at) VALUES
(12001,'programming-flow-learn',UTC_TIMESTAMP(6)),
(12002,'programming-flow-practice',UTC_TIMESTAMP(6)),
(12003,'programming-flow-recall',UTC_TIMESTAMP(6));

INSERT INTO task_template_versions(id,template_id,version_number,graph_version_id,resource_version_id,title,instructions,checklist,activity_type,evaluation_mode,difficulty,estimated_minutes,min_minutes,max_minutes,variant_group_key,status,content_source,created_at) VALUES
(13001,12001,1,1,11001,'Learn: trace program flow','Read the bounded explanation and trace one example step by step.',JSON_ARRAY(JSON_OBJECT('id','read','label','I read the short explanation'),JSON_OBJECT('id','trace','label','I traced values after each step')),'LEARN','SELF_REPORT',1,10,10,10,'programming-flow','ACTIVE','CURATED',UTC_TIMESTAMP(6)),
(13002,12002,1,1,11002,'Practice: counter exercise','Work through the counter exercise yourself before checking it.',JSON_ARRAY(JSON_OBJECT('id','attempt','label','I worked through the exercise'),JSON_OBJECT('id','check','label','I checked my result and corrected mistakes')),'PRACTICE','SELF_REPORT',1,15,15,15,'programming-flow','ACTIVE','CURATED',UTC_TIMESTAMP(6)),
(13003,12003,1,1,11003,'Recall: explain the flow','Explain the exercise from memory and then compare with the explanation.',JSON_ARRAY(JSON_OBJECT('id','recall','label','I explained it without notes'),JSON_OBJECT('id','compare','label','I compared my explanation with the notes')),'RECALL','SELF_REPORT',1,5,5,5,'programming-flow','ACTIVE','CURATED',UTC_TIMESTAMP(6));

INSERT INTO task_template_translations(task_template_version_id,locale,title,instructions,checklist) VALUES
(13001,'vi-VN','Học: lần theo luồng chương trình','Đọc phần giải thích ngắn và lần theo một ví dụ từng bước.',JSON_ARRAY(JSON_OBJECT('id','read','label','Tôi đã đọc phần giải thích ngắn'),JSON_OBJECT('id','trace','label','Tôi đã ghi giá trị sau từng bước'))),
(13002,'vi-VN','Thực hành: bài biến đếm','Tự làm bài biến đếm trước khi kiểm tra kết quả.',JSON_ARRAY(JSON_OBJECT('id','attempt','label','Tôi đã tự làm bài'),JSON_OBJECT('id','check','label','Tôi đã kiểm tra kết quả và sửa lỗi'))),
(13003,'vi-VN','Nhớ lại: giải thích luồng chạy','Giải thích bài từ trí nhớ rồi đối chiếu với phần đã học.',JSON_ARRAY(JSON_OBJECT('id','recall','label','Tôi đã giải thích mà không xem ghi chú'),JSON_OBJECT('id','compare','label','Tôi đã đối chiếu với ghi chú')));

INSERT INTO task_template_knowledge(task_template_version_id,graph_version_id,knowledge_node_id,mapping_role,dimension,weight) VALUES
(13001,1,1001,'PRIMARY','UNDERSTANDING',1.0000),
(13002,1,1001,'PRIMARY','APPLICATION',1.0000),
(13003,1,1001,'PRIMARY','RECALL',1.0000);

INSERT INTO learning_sequences(id,sequence_key,version_number,graph_version_id,title,description,title_vi,description_vi,status,created_at) VALUES
(14001,'programming-flow-foundations',1,1,'Program flow: learn, practice, recall','A self-selected 30-minute foundation sequence. Completion records activity, not mastery.','Luồng chương trình: học, thực hành, nhớ lại','Chuỗi nền tảng 30 phút do bạn tự chọn. Hoàn thành chỉ ghi nhận hoạt động, không xác nhận mức độ thành thạo.','ACTIVE',UTC_TIMESTAMP(6));

INSERT INTO learning_sequence_items(sequence_id,position,task_template_version_id) VALUES
(14001,1,13001),(14001,2,13002),(14001,3,13003);
