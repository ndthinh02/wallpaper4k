# Walper — tiến trình triển khai

## Bộ sưu tập cá nhân

Trong tab **Bộ sưu tập → Của tôi**, nhấn **+** để tạo bộ sưu tập, nhập tên và chọn một trong năm màu. Thẻ bộ sưu tập hiển thị ảnh bìa từ ảnh được thêm gần nhất và số hình nền. Tab **Khám phá** giữ các danh mục có sẵn. Giao diện dùng Roboto, hỗ trợ sáng/tối và Việt/Anh; chuyển tab, đổi màu, mở/đóng bảng tạo và cập nhật danh sách có animation.

Trong một bộ sưu tập, nhấn **+** để chọn ảnh từ yêu thích, tải xuống và trang chủ hiện tại. Màn xem chi tiết có nút **Lưu vào bộ sưu tập** và cho phép tạo bộ sưu tập kèm ảnh đầu tiên trong cùng giao dịch. **Quản lý** cho phép bỏ ảnh; nút chỉnh sửa cho phép đổi tên/màu. Xóa có xác nhận và giữ nguyên ảnh đã tải cùng yêu thích. Việc lưu vào bộ sưu tập lưu thông tin ảnh; ảnh gốc chỉ dùng ngoại tuyến khi đã tải xuống.

Dữ liệu lưu bằng Room v2 với migration từ v1, khóa ngoại xóa liên kết theo bộ sưu tập và chống trùng ảnh/tên. `CollectionsDatabaseTest` kiểm tra migration giữ dữ liệu cũ, dữ liệu tồn tại sau mở lại, đổi tên, chống trùng, xóa liên kết và rollback giao dịch. Các kiểm thử này cần thiết bị Android: `./gradlew connectedDebugAndroidTest`.

Kiểm tra cập nhật ngày 09/10/2026: **`assembleDebug` và `compileDebugAndroidTestKotlin` thành công**. APK được tạo tại `app/build/outputs/apk/debug/app-debug.apk`. Build offline sử dụng init script `.verification/collections-check.gradle` để lấy các artifact WorkManager, Tracing và LiveData đúng phiên bản từ cache có sẵn trên máy; không thay đổi phiên bản dependency của ứng dụng. Chưa chạy instrumentation test hay xem giao diện trên thiết bị do ADB bị chặn quyền ghi.

Màn chi tiết hiển thị toàn bộ ảnh đúng tỉ lệ bằng `ContentScale.Fit`, hỗ trợ vuốt trái/phải để xem ảnh tiếp theo/trước trong danh sách đã mở, giữ thứ tự Favorites/Downloads và bộ sưu tập cá nhân đang chọn. Khi zoom, kéo ảnh dùng để pan với giới hạn theo kích thước ảnh thực tế trong khung; chụm hai ngón không chuyển ảnh. Đến đầu/cuối danh sách không chuyển vòng. Vuốt thay màn chi tiết hiện tại nên Back quay về danh sách.

Đã đọc `prompt.txt` và ảnh trong `design/walper/`. Giữ applicationId/package `com.example.wallpaper`, AGP 9.4.1, Kotlin Compose plugin 2.2.10, Gradle 9.6.0, minSdk 24 và compile/targetSdk 37. Tên hiển thị là **Walper**.

## Mã đã triển khai

- Splash theo system theme, onboarding ba trang có swipe và lưu hoàn tất bằng DataStore.
- Home: Wallhaven SFW, Trending/Recent/Random, seed, phân trang theo metadata, chống trùng ID, hai kiểu lưới, kéo refresh, retry và yêu thích.
- Collections: 16 danh mục local, ảnh bìa lấy khi card xuất hiện và lưu Room; lọc sorting, màu, tỷ lệ và độ phân giải tối thiểu.
- Search: debounce 400 ms, hủy yêu cầu cũ, lịch sử có thể xóa, IME search, tìm danh mục Việt/Anh và phân trang.
- Detail: thumbnail trước ảnh gốc, zoom/pan, ẩn controls, thông tin nguồn, chia sẻ URL/file, preview khóa minh họa và ưu tiên URI local.
- Favorites và Downloads lưu Room; Downloads có ảnh local, trạng thái file thiếu/tải lỗi, thử lại, xóa lịch sử riêng với xóa file có xác nhận và xử lý yêu cầu hệ thống.
- Tải bằng unique WorkManager work, tiến trình trong Detail, MediaStore `Pictures/Walper`, quyền lưu trữ chỉ trên API 24–28, dọn file lỗi và kiểm tra ảnh trước khi báo hoàn tất.
- Đặt hình nền chính/khóa/cả hai: crop theo màn hình, decode giới hạn bộ nhớ, thực hiện ngoài main thread và chỉ báo thành công khi WallpaperManager trả kết quả. Có tùy chọn mở trình hệ thống, không khẳng định kết quả của trình đó.
- Sidebar chế độ khách, một cây nội dung; Settings sáng/tối/hệ thống, Việt/Anh, thông báo và xóa cache ảnh. Có 154 resource chuỗi cho mỗi ngôn ngữ.
- Tự đổi từ file đã tải bằng unique periodic work, chọn 1/6/24 giờ và đích, tránh lặp ảnh trước; tắt khi không còn ảnh hợp lệ hoặc thiết bị không cho đặt.
- Lịch sử thông báo local từ sự kiện tải/đặt/tự đổi thật, đánh dấu đọc và vuốt xóa; thông báo hệ thống theo quyền người dùng.
- Privacy mô tả luồng dữ liệu thực tế. Báo cáo lỗi có form, xem trước và Android Sharesheet, không giả gửi đến hỗ trợ.

## Kết quả kiểm tra ban đầu (trước bộ sưu tập cá nhân)

- **4/4 unit test bộ lọc API đã chạy và đạt**, biên dịch trực tiếp bằng Kotlin 2.2.10 và chạy JUnit 4.13.2 từ thư viện có sẵn. Đây không phải kết quả kiểm thử toàn ứng dụng.
- Kiểm tra XML Việt/Anh: tên 154 resource tương ứng khớp nhau.
- Kiểm tra bổ sung với classpath cache: resource được biên dịch, KSP sinh `LocalDao_Impl` và `LocalDatabase_Impl`. Kotlin còn báo lỗi tham chiếu WorkManager do dependency bị loại khỏi lần kiểm tra bổ sung. Classpath kiểm tra này ép Compose 1.10.4 có sẵn; **không xác nhận bản build production**.
- **`assembleDebug` đã chạy nhưng thất bại**: thiếu `androidx.work:work-runtime-ktx:2.10.5` trong cache. Chạy online cũng bị môi trường chặn kết nối (`Permission denied: getsockopt`). Một số artifact Compose cần tải thêm khi giải quyết dependency đầy đủ.
- **Lần kiểm tra ban đầu chưa tạo APK.** Chưa xác nhận luồng API/tải/đặt/khởi động lại/tự đổi trên thiết bị. ADB trong môi trường hiện tại không tạo được thư mục `.android`. Chưa có screenshot app để so Home, Collections, Detail và Settings.

Khi môi trường có quyền tải dependency, chạy từ thư mục project:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
```

APK debug: `app/build/outputs/apk/debug/app-debug.apk`.

## Thiết kế và giai đoạn sau

Logo/wordmark tách từ image 1; onboarding tách từ image 8–10 và 12–14; illustration empty/error tách từ image 31, 35–36, 43–44, 47–48, 54–55. Chỉ dùng vùng asset sạch, không dùng screenshot thay giao diện. Font hệ thống Android và Material Icons thay gần đúng vì chưa có font/icon riêng; dialog và sheet dùng Material 3. Chưa khẳng định pixel-perfect.

Auth, OTP, social login, hồ sơ, xóa tài khoản, Premium, thanh toán và ảnh độc quyền giữ cho giai đoạn sau. Bản này dùng chế độ khách, không có backend hoặc chức năng thành công giả.

Nguồn API đã đọc: [Wallhaven API](https://wallhaven.cc/help/api), truy cập được qua [tài liệu cùng dịch vụ trên www.whvn.cc](https://www.whvn.cc/help/api). Các endpoint thực tế vẫn là `https://wallhaven.cc/api/v1/`.
