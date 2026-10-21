# 공개 배포 준비

현재 결과물은 디버그 서명된 설치 테스트용 APK입니다. Google Play에 올린 상태가 아닙니다.

## 제품

- 실제 삼성·픽셀 기기에서 Android 10, 13, 14, 16 계열을 검증합니다.
- 사진 수천 장 환경의 목록 성능, OCR 소요 시간, 메모리와 배터리 사용량을 측정합니다.
- 한국어·영문 검색, 폴더별 자동 모으기, 일부 사진 허용, 거부·권한 회수를 검증합니다.
- 캡처 허용·취소·잠금·회전·앱 단위 공유·전체 화면 공유·보안 화면을 검증합니다.
- 카운트다운 오버레이 권한 허용·거부·회수와 캡처 결과에서 숫자가 빠지는지 검증합니다.
- 빠른 설정 타일을 추가하고 앱 종료 상태와 잠금 해제 후 캡처를 검증합니다.
- 사진 단건·다건 삭제, 취소, Android 10의 파일별 재승인을 검증합니다.
- 화면 확대와 TalkBack, 큰 글씨에서 버튼과 목록을 검증합니다.
- 오류 진단과 접근성, 화면 확대/축소 등 추가 사용성 개선을 결정합니다.

## 배포

- 최종 앱 이름, 고유 applicationId, 개발자 표시 이름과 연락처를 확정합니다.
- 출시용 아이콘, 휴대폰 스크린샷, 스토어 소개문을 준비합니다.
- 개발자 계정에서 요구되는 테스트 및 계정 확인 절차를 확인합니다.
- 소유자가 관리하는 출시 서명 키와 Play App Signing을 구성합니다. 키를 소스에 넣지 않습니다.
- 출시 빌드의 AAB, 네이티브 라이브러리 16 KB 페이지 호환성, 최신 target API 요건을 확인합니다.
- 사진 관리가 핵심 기능임을 스토어 소개와 권한 신고에서 일관되게 설명합니다.
- `READ_MEDIA_IMAGES` 접근 심사는 Google Play 판단이며 승인을 보장할 수 없습니다.
- MediaProjection 포그라운드 서비스 사용 목적과 필요한 시연 자료를 제출합니다.
- 실제 최종 의존성과 동작 기준으로 Data safety 답변을 작성합니다.
- `PRIVACY-DRAFT.md`에 운영 주체·문의 연락처를 넣고 공개 URL에 게시합니다.

## 공식 참고

- [사진·동영상 접근 정책](https://support.google.com/googleplay/android-developer/answer/14115180)
- [부분 사진 접근](https://developer.android.com/about/versions/14/changes/partial-photo-video-access)
- [화면 공유 MediaProjection](https://developer.android.com/media/grow/media-projection)
- [ML Kit 한국어 글자 인식](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)
- [16 KB 페이지 지원](https://developer.android.com/guide/practices/page-sizes)
