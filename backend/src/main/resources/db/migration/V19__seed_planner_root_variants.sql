INSERT INTO resources(id,resource_key,provider,source_type,license_code,created_at) VALUES
(10004,'command-line-short','SkillPath','PROJECT_AUTHORED','SKILLPATH-ORIGINAL',UTC_TIMESTAMP(6)),
(10005,'http-short','SkillPath','PROJECT_AUTHORED','SKILLPATH-ORIGINAL',UTC_TIMESTAMP(6)),
(10006,'sql-short','SkillPath','PROJECT_AUTHORED','SKILLPATH-ORIGINAL',UTC_TIMESTAMP(6));

INSERT INTO resource_versions(id,resource_id,version_number,resource_type,title,body,content_ref,section_key,difficulty,estimated_minutes,status,created_at) VALUES
(11004,10004,1,'INTERNAL','Navigate a project from the terminal','In a disposable project folder, print the current directory, list its files, enter a subfolder, return to the parent, and inspect a text file. Verify the path before changing files.','skillpath://command-line-short/1','navigation',1,12,'ACTIVE',UTC_TIMESTAMP(6)),
(11005,10005,1,'INTERNAL','Trace one HTTP exchange','For a sample GET request, identify the method, path, request headers, response status, response headers, and body. Explain which part conveys success and which part carries data.','skillpath://http-short/1','request-response',2,15,'ACTIVE',UTC_TIMESTAMP(6)),
(11006,10006,1,'INTERNAL','Read a small relational query','Imagine students(id,name) and submissions(id,student_id,score). Identify the primary and foreign keys, then describe which rows a JOIN with score >= 80 returns. Check the join condition before filtering.','skillpath://sql-short/1','join-filter',2,18,'ACTIVE',UTC_TIMESTAMP(6));

INSERT INTO resource_version_translations(resource_version_id,locale,title,body) VALUES
(11004,'vi-VN','Di chuyển trong dự án bằng terminal','Trong thư mục dự án thử nghiệm, in đường dẫn hiện tại, liệt kê tệp, vào thư mục con, trở về thư mục cha và xem một tệp văn bản. Kiểm tra đường dẫn trước khi thay đổi tệp.'),
(11005,'vi-VN','Lần theo một lượt trao đổi HTTP','Với yêu cầu GET mẫu, xác định phương thức, đường dẫn, header yêu cầu, mã trạng thái, header phản hồi và nội dung. Giải thích phần nào báo thành công và phần nào mang dữ liệu.'),
(11006,'vi-VN','Đọc một truy vấn quan hệ nhỏ','Giả sử có students(id,name) và submissions(id,student_id,score). Xác định khóa chính và khóa ngoại, rồi mô tả các dòng mà JOIN với score >= 80 trả về. Kiểm tra điều kiện nối trước khi lọc.');

INSERT INTO knowledge_resources(resource_version_id,graph_version_id,knowledge_node_id,section_key,purpose,estimated_minutes) VALUES
(11004,1,1002,'navigation','FOUNDATION',12),
(11005,1,1004,'request-response','FOUNDATION',15),
(11006,1,1005,'join-filter','FOUNDATION',18);

INSERT INTO task_templates(id,template_key,created_at) VALUES
(12004,'command-line-short',UTC_TIMESTAMP(6)),
(12005,'http-short',UTC_TIMESTAMP(6)),
(12006,'sql-short',UTC_TIMESTAMP(6));

INSERT INTO task_template_versions(id,template_id,version_number,graph_version_id,resource_version_id,title,instructions,checklist,activity_type,evaluation_mode,difficulty,estimated_minutes,min_minutes,max_minutes,variant_group_key,status,content_source,created_at) VALUES
(13004,12004,1,1,11004,'Learn: navigate safely','Follow the short terminal exercise in a disposable folder.',JSON_ARRAY(JSON_OBJECT('id','navigate','label','I navigated to and from a subfolder'),JSON_OBJECT('id','verify','label','I verified the path and inspected a file')),'LEARN','SELF_REPORT',1,12,12,12,'command-line','ACTIVE','CURATED',UTC_TIMESTAMP(6)),
(13005,12005,1,1,11005,'Learn: HTTP request and response','Annotate the parts of one sample request and response.',JSON_ARRAY(JSON_OBJECT('id','request','label','I identified method, path, and request headers'),JSON_OBJECT('id','response','label','I identified status, headers, and response body')),'LEARN','SELF_REPORT',2,15,15,15,'http','ACTIVE','CURATED',UTC_TIMESTAMP(6)),
(13006,12006,1,1,11006,'Learn: relational join','Sketch the two tables and trace the JOIN before the filter.',JSON_ARRAY(JSON_OBJECT('id','keys','label','I identified the table keys'),JSON_OBJECT('id','join','label','I traced the join and score filter')),'LEARN','SELF_REPORT',2,18,18,18,'sql','ACTIVE','CURATED',UTC_TIMESTAMP(6));

INSERT INTO task_template_translations(task_template_version_id,locale,title,instructions,checklist) VALUES
(13004,'vi-VN','Học: điều hướng an toàn','Làm bài thực hành terminal ngắn trong thư mục thử nghiệm.',JSON_ARRAY(JSON_OBJECT('id','navigate','label','Tôi đã vào và ra một thư mục con'),JSON_OBJECT('id','verify','label','Tôi đã kiểm tra đường dẫn và xem một tệp'))),
(13005,'vi-VN','Học: yêu cầu và phản hồi HTTP','Ghi chú các phần của một yêu cầu và phản hồi mẫu.',JSON_ARRAY(JSON_OBJECT('id','request','label','Tôi đã xác định phương thức, đường dẫn và header yêu cầu'),JSON_OBJECT('id','response','label','Tôi đã xác định trạng thái, header và nội dung phản hồi'))),
(13006,'vi-VN','Học: phép nối quan hệ','Phác hai bảng và lần theo JOIN trước khi lọc.',JSON_ARRAY(JSON_OBJECT('id','keys','label','Tôi đã xác định các khóa của bảng'),JSON_OBJECT('id','join','label','Tôi đã lần theo phép nối và điều kiện lọc điểm')));

INSERT INTO task_template_knowledge(task_template_version_id,graph_version_id,knowledge_node_id,mapping_role,dimension,weight) VALUES
(13004,1,1002,'PRIMARY','UNDERSTANDING',1.0000),
(13005,1,1004,'PRIMARY','UNDERSTANDING',1.0000),
(13006,1,1005,'PRIMARY','UNDERSTANDING',1.0000);
