INSERT INTO goal_template_translations (
    goal_template_id, locale, display_name, description, created_at
) VALUES (
    1,
    'vi-VN',
    'Thực tập sinh Java Backend',
    'Xây dựng các kỹ năng nền tảng cần thiết cho một vị trí thực tập Java backend.',
    UTC_TIMESTAMP(6)
);

INSERT INTO knowledge_node_translations (
    graph_version_id, knowledge_node_id, locale, name, description, created_at
) VALUES
    (1, 1001, 'vi-VN', 'Nền tảng lập trình', 'Viết và phân tích biến, luồng điều khiển, hàm và cách chia nhỏ chương trình cơ bản.', UTC_TIMESTAMP(6)),
    (1, 1002, 'vi-VN', 'Dòng lệnh', 'Điều hướng tệp, chạy chương trình và kiểm tra tiến trình an toàn trong môi trường dòng lệnh.', UTC_TIMESTAMP(6)),
    (1, 1003, 'vi-VN', 'Nền tảng Git', 'Tạo commit, xem lịch sử, tạo nhánh, hợp nhất và cộng tác mà không làm mất công việc.', UTC_TIMESTAMP(6)),
    (1, 1004, 'vi-VN', 'Nền tảng HTTP', 'Giải thích request, response, method, status code, header, cache và giao tiếp stateless.', UTC_TIMESTAMP(6)),
    (1, 1005, 'vi-VN', 'Dữ liệu quan hệ và SQL', 'Mô hình hóa dữ liệu quan hệ và viết truy vấn SQL lọc, join, tổng hợp và transaction chính xác.', UTC_TIMESTAMP(6)),
    (1, 1006, 'vi-VN', 'Ngôn ngữ Java', 'Xây dựng và chạy chương trình Java có kiểu dữ liệu bằng cú pháp, method, class và công cụ cốt lõi.', UTC_TIMESTAMP(6)),
    (1, 1007, 'vi-VN', 'Thiết kế hướng đối tượng', 'Mô hình hóa object gắn kết với trách nhiệm rõ ràng, encapsulation, interface và composition.', UTC_TIMESTAMP(6)),
    (1, 1008, 'vi-VN', 'Collections và Generics', 'Lựa chọn và sử dụng Java collection cùng generic type với hành vi equality và iteration chính xác.', UTC_TIMESTAMP(6)),
    (1, 1009, 'vi-VN', 'Xử lý ngoại lệ', 'Thiết kế luồng lỗi dự đoán được và sử dụng checked, unchecked cùng domain exception phù hợp.', UTC_TIMESTAMP(6)),
    (1, 1010, 'vi-VN', 'Kiểm thử đơn vị', 'Viết test độc lập, deterministic để kiểm chứng hành vi và các trường hợp biên quan trọng.', UTC_TIMESTAMP(6)),
    (1, 1011, 'vi-VN', 'Thiết kế REST API', 'Thiết kế HTTP API hướng tài nguyên với validation, contract ổn định và lỗi nhất quán.', UTC_TIMESTAMP(6)),
    (1, 1012, 'vi-VN', 'Spring Boot', 'Xây dựng dịch vụ Spring Boot phân lớp với dependency injection, cấu hình và REST controller.', UTC_TIMESTAMP(6)),
    (1, 1013, 'vi-VN', 'Lưu trữ với JPA', 'Ánh xạ aggregate vào cơ sở dữ liệu quan hệ và triển khai persistence theo transaction với boundary rõ ràng.', UTC_TIMESTAMP(6)),
    (1, 1014, 'vi-VN', 'Validation và hợp đồng lỗi', 'Kiểm tra input ở transport boundary và trả về lỗi an toàn, nhất quán, máy có thể đọc.', UTC_TIMESTAMP(6)),
    (1, 1015, 'vi-VN', 'Xác thực và phân quyền', 'Bảo vệ thao tác backend bằng identity đã xác thực, authorization, session và các biện pháp phòng vệ web phổ biến.', UTC_TIMESTAMP(6)),
    (1, 1016, 'vi-VN', 'Kiểm thử tích hợp', 'Xác minh API, database, migration, security và transaction trong môi trường gần production.', UTC_TIMESTAMP(6)),
    (1, 1017, 'vi-VN', 'Docker và nền tảng triển khai', 'Đóng gói và chạy ứng dụng backend với cấu hình tái lập được, health check và dependency database.', UTC_TIMESTAMP(6));

INSERT INTO question_version_translations (
    question_version_id, locale, prompt, options, created_at
) VALUES
    (3101, 'vi-VN',
     'Cấu trúc điều khiển nào phù hợp nhất để lặp lại một thao tác khi điều kiện vẫn còn đúng?',
     JSON_ARRAY(
        JSON_OBJECT('id', 'if', 'label', 'câu lệnh if'),
        JSON_OBJECT('id', 'while', 'label', 'vòng lặp while'),
        JSON_OBJECT('id', 'class', 'label', 'khai báo class'),
        JSON_OBJECT('id', 'import', 'label', 'câu lệnh import')
     ), UTC_TIMESTAMP(6)),
    (3102, 'vi-VN',
     'Những lệnh Git nào giúp xem lịch sử hiện có mà không tạo commit mới? Chọn tất cả đáp án phù hợp.',
     JSON_ARRAY(
        JSON_OBJECT('id', 'log', 'label', 'git log'),
        JSON_OBJECT('id', 'show', 'label', 'git show'),
        JSON_OBJECT('id', 'commit', 'label', 'git commit'),
        JSON_OBJECT('id', 'init', 'label', 'git init')
     ), UTC_TIMESTAMP(6)),
    (3103, 'vi-VN',
     'Phương thức HTTP nào thường được dùng để lấy tài nguyên mà không yêu cầu thay đổi trạng thái?',
     JSON_ARRAY(
        JSON_OBJECT('id', 'get', 'label', 'GET'),
        JSON_OBJECT('id', 'post', 'label', 'POST'),
        JSON_OBJECT('id', 'patch', 'label', 'PATCH'),
        JSON_OBJECT('id', 'delete', 'label', 'DELETE')
     ), UTC_TIMESTAMP(6)),
    (3104, 'vi-VN',
     'Những thành phần SQL nào thường được dùng khi tính số lượng theo từng nhóm? Chọn tất cả đáp án phù hợp.',
     JSON_ARRAY(
        JSON_OBJECT('id', 'count', 'label', 'COUNT(...)'),
        JSON_OBJECT('id', 'group-by', 'label', 'GROUP BY'),
        JSON_OBJECT('id', 'drop-table', 'label', 'DROP TABLE'),
        JSON_OBJECT('id', 'grant', 'label', 'GRANT')
     ), UTC_TIMESTAMP(6)),
    (3105, 'vi-VN',
     'Trong Java, từ khóa nào ngăn một biến cục bộ được gán lại sau khi khởi tạo?',
     JSON_ARRAY(
        JSON_OBJECT('id', 'final', 'label', 'final'),
        JSON_OBJECT('id', 'static', 'label', 'static'),
        JSON_OBJECT('id', 'public', 'label', 'public'),
        JSON_OBJECT('id', 'throws', 'label', 'throws')
     ), UTC_TIMESTAMP(6)),
    (3106, 'vi-VN',
     'Những lựa chọn nào hỗ trợ object composition với trách nhiệm rõ ràng? Chọn tất cả đáp án phù hợp.',
     JSON_ARRAY(
        JSON_OBJECT('id', 'constructor-dependency', 'label', 'Truyền dependency qua constructor'),
        JSON_OBJECT('id', 'small-interface', 'label', 'Phụ thuộc vào một interface tập trung'),
        JSON_OBJECT('id', 'global-state', 'label', 'Lưu mọi dependency trong global mutable state'),
        JSON_OBJECT('id', 'god-object', 'label', 'Đưa các hành vi không liên quan vào một object')
     ), UTC_TIMESTAMP(6)),
    (3107, 'vi-VN',
     'Java collection nào được thiết kế để giữ các giá trị duy nhất, không có phần tử trùng lặp?',
     JSON_ARRAY(
        JSON_OBJECT('id', 'set', 'label', 'Set'),
        JSON_OBJECT('id', 'list', 'label', 'List'),
        JSON_OBJECT('id', 'queue', 'label', 'Queue'),
        JSON_OBJECT('id', 'string-builder', 'label', 'StringBuilder')
     ), UTC_TIMESTAMP(6)),
    (3108, 'vi-VN',
     'Những đặc tính nào làm cho một unit test đáng tin cậy? Chọn tất cả đáp án phù hợp.',
     JSON_ARRAY(
        JSON_OBJECT('id', 'deterministic', 'label', 'Cho cùng kết quả với cùng input'),
        JSON_OBJECT('id', 'isolated', 'label', 'Cô lập hành vi đang được kiểm thử'),
        JSON_OBJECT('id', 'order-dependent', 'label', 'Phụ thuộc vào một test khác chạy trước'),
        JSON_OBJECT('id', 'production-data', 'label', 'Yêu cầu dữ liệu production')
     ), UTC_TIMESTAMP(6));
