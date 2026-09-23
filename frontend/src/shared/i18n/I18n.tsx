import { useQueryClient } from '@tanstack/react-query'
import {
  createContext,
  type ReactNode,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react'
import {
  type AppLocale,
  getPreferredLocale,
  LOCALE_STORAGE_KEY,
} from '../config/localization'

const en = {
  'app.badge': 'Adaptive Planner',
  'app.tagline': 'One clear next step',
  'language.label': 'Language',
  'language.vi': 'Tiếng Việt',
  'language.en': 'English',
  'common.email': 'Email address',
  'common.password': 'Password',
  'common.timezone': 'Timezone',
  'common.loading': 'Loading…',
  'common.signIn': 'Sign in',
  'common.returnGoal': 'Return to active goal',
  'error.generic': 'Something went wrong. Please try again.',
  'error.reference': 'Reference: {id}',
  'error.UNAUTHENTICATED': 'Your session has ended. Please sign in again.',
  'error.ACTIVE_GOAL_NOT_FOUND': 'No active goal was found.',
  'error.DIAGNOSTIC_UNAVAILABLE_FOR_GRAPH_VERSION':
    'The diagnostic is not available for this learning path yet.',
  'error.ASSESSMENT_SESSION_NOT_FOUND':
    'This diagnostic session was not found.',
  'error.ASSESSMENT_SESSION_EXPIRED': 'This diagnostic session has expired.',
  'error.ASSESSMENT_NOT_COMPLETED':
    'Complete the diagnostic to view its result.',
  'error.QUESTION_ALREADY_ANSWERED':
    'This answer was already saved. Loading the next question…',
  'error.INVALID_ANSWER_SELECTION': 'Choose a valid answer and try again.',
  'login.eyebrow': 'Welcome back',
  'login.title': 'Sign in',
  'login.lede': 'Continue your personalized learning route.',
  'login.pending': 'Signing in…',
  'login.new': 'New to SkillPath?',
  'login.create': 'Create an account',
  'register.eyebrow': 'Start your route',
  'register.title': 'Create account',
  'register.lede': 'Tell us who you are. Your first goal comes next.',
  'register.name': 'Display name',
  'register.pending': 'Creating account…',
  'register.submit': 'Create account',
  'register.existing': 'Already registered?',
  'validation.email': 'Enter a valid email address.',
  'validation.passwordRequired': 'Enter your password.',
  'validation.passwordLength': 'Use at least 12 characters.',
  'validation.name': 'Enter your name.',
  'validation.timezoneRequired': 'Timezone is required.',
  'validation.goal': 'Choose a goal.',
  'validation.targetDate': 'Choose a target date.',
  'goalSetup.loading': 'Loading learning paths…',
  'goalSetup.empty': 'No learning paths are available yet.',
  'goalSetup.eyebrow': 'Choose the destination',
  'goalSetup.title': 'Create your first goal',
  'goalSetup.lede':
    'We will use your deadline and daily budget to calculate the shortest defensible learning route.',
  'goalSetup.track': 'Curated career track',
  'goalSetup.select': 'Select a path',
  'goalSetup.date': 'Target readiness date',
  'goalSetup.budget': 'Daily study budget',
  'goalSetup.quickMinutes': 'Quick daily minutes selection',
  'goalSetup.minutes': '{minutes} minutes',
  'goalSetup.pending': 'Calculating route…',
  'goalSetup.submit': 'Generate learning route',
  'goal.loading': 'Calculating your optimal learning route…',
  'goal.noRoute': 'No active route',
  'goal.noGoal': 'No active goal yet',
  'goal.noGoalDetail':
    'Your route starts after you set a destination and daily study budget.',
  'goal.chooseTrack': 'Choose learning track',
  'goal.commandCenter': 'Today Command Center',
  'goal.welcome': 'Welcome, {name}',
  'goal.routeReady':
    'Your route for Java Backend Internship readiness is calibrated.',
  'goal.signingOut': 'Signing out…',
  'goal.signOut': 'Sign out',
  'goal.category': 'Diagnostic • Evidence baseline',
  'goal.durationLabel': 'Estimated duration {minutes} minutes',
  'goal.allocated': '⏱️ {minutes} mins allocated today',
  'goal.diagnosticTitle': 'Java Backend initial diagnostic',
  'goal.why': 'Why this task:',
  'goal.rationale':
    'Eight short objective questions will create concept-level evidence for the next Knowledge State phase. Evidence describes what each attempt demonstrated; it is not a mastery decision.',
  'goal.questions': '8 questions',
  'goal.duration': '6–8 minutes',
  'goal.resumeSafe': 'Safe to resume',
  'goal.preparing': 'Preparing diagnostic…',
  'goal.start': 'Start or resume diagnostic',
  'goal.retention':
    'Your answers are stored securely and can be resumed for seven days.',
  'goal.specifications': 'Active Goal Specifications',
  'goal.targetDate': 'Target Date',
  'goal.dailyBudget': 'Daily Budget',
  'goal.perDay': '{minutes} min / day',
  'goal.status': 'Status',
  'goal.active': 'Active',
  'goal.milestone': 'Current milestone:',
  'goal.milestoneDetail':
    'Complete the diagnostic to produce the observational evidence that Phase 4 will convert into a Knowledge State.',
  'auth.session': 'Session',
  'auth.ended': 'Session ended',
  'auth.endedDetail': 'Sign in again to continue with your learning route.',
  'diagnostic.eyebrow': 'Diagnostic',
  'diagnostic.noSession': 'No diagnostic session selected',
  'diagnostic.noSessionDetail':
    'Start or resume your diagnostic from the active goal.',
  'diagnostic.loadingQuestion': 'Loading your next question…',
  'diagnostic.loadingResult': 'Preparing your evidence summary…',
  'diagnostic.noResult': 'No diagnostic result is available.',
  'diagnostic.title': 'Java Backend diagnostic',
  'diagnostic.progress': 'Question {position} of {total}',
  'diagnostic.aboutMinutes': 'About {minutes} min',
  'diagnostic.progressLabel': 'Diagnostic progress',
  'diagnostic.selectMany': 'Select all that apply.',
  'diagnostic.selectOne': 'Select one answer.',
  'diagnostic.confidence': 'How confident are you in this answer?',
  'diagnostic.confidenceLow': 'Not very confident',
  'diagnostic.confidenceMedium': 'Somewhat confident',
  'diagnostic.confidenceHigh': 'Very confident',
  'diagnostic.leave': 'Save and leave',
  'diagnostic.saving': 'Saving answer…',
  'diagnostic.submit': 'Submit and continue',
  'diagnostic.boundary':
    'This diagnostic records evidence from each attempt. It does not declare mastery or choose your next learning task.',
  'result.complete': 'Diagnostic complete',
  'result.title': 'Evidence baseline recorded',
  'result.scoreLabel': 'Observed objective answer score',
  'result.score': 'Observed answer score',
  'result.notMastery': 'Not a mastery or readiness score',
  'result.demonstrated': 'What these attempts demonstrated',
  'result.observed': '{score}% observed',
  'result.dimension.RECOGNITION': 'recognition',
  'result.dimension.UNDERSTANDING': 'understanding',
  'diagnostic.expired': 'Diagnostic expired',
  'diagnostic.expiredTitle': 'This diagnostic session has expired',
  'diagnostic.expiredDetail':
    'Return to your active goal to start a fresh pinned diagnostic session.',
  'knowledge.open': 'View knowledge state',
  'knowledge.loading': 'Building your knowledge state…',
  'knowledge.eyebrow': 'Knowledge State',
  'knowledge.title': 'What SkillPath currently estimates you know',
  'knowledge.lede':
    'This projection combines observed evidence and changes as evidence ages.',
  'knowledge.empty': 'No knowledge state yet',
  'knowledge.emptyDetail':
    'Complete the diagnostic, then allow a moment for its evidence to be processed.',
  'knowledge.mastery': 'Estimated mastery',
  'knowledge.confidence': 'Confidence',
  'knowledge.evidence': '{count} evidence observations',
  'knowledge.boundary':
    'Knowledge State estimates what you know. Personalized planning begins in Phase 5.',
  'knowledge.status.UNKNOWN': 'Unknown',
  'knowledge.status.LEARNING': 'Learning',
  'knowledge.status.PROVISIONAL': 'Provisional',
  'knowledge.status.MASTERED': 'Mastered',
  'knowledge.status.REVIEW_DUE': 'Review due',
} as const

type TranslationKey = keyof typeof en
type Replacements = Record<string, string | number>

const vi: Record<TranslationKey, string> = {
  'app.badge': 'Lộ trình thích ứng',
  'app.tagline': 'Một bước tiếp theo rõ ràng',
  'language.label': 'Ngôn ngữ',
  'language.vi': 'Tiếng Việt',
  'language.en': 'English',
  'common.email': 'Địa chỉ email',
  'common.password': 'Mật khẩu',
  'common.timezone': 'Múi giờ',
  'common.loading': 'Đang tải…',
  'common.signIn': 'Đăng nhập',
  'common.returnGoal': 'Quay lại mục tiêu đang học',
  'error.generic': 'Đã xảy ra lỗi. Vui lòng thử lại.',
  'error.reference': 'Mã tham chiếu: {id}',
  'error.UNAUTHENTICATED':
    'Phiên đăng nhập đã kết thúc. Vui lòng đăng nhập lại.',
  'error.ACTIVE_GOAL_NOT_FOUND': 'Không tìm thấy mục tiêu đang hoạt động.',
  'error.DIAGNOSTIC_UNAVAILABLE_FOR_GRAPH_VERSION':
    'Bài đánh giá chưa khả dụng cho lộ trình này.',
  'error.ASSESSMENT_SESSION_NOT_FOUND': 'Không tìm thấy phiên đánh giá này.',
  'error.ASSESSMENT_SESSION_EXPIRED': 'Phiên đánh giá này đã hết hạn.',
  'error.ASSESSMENT_NOT_COMPLETED':
    'Hãy hoàn thành bài đánh giá để xem kết quả.',
  'error.QUESTION_ALREADY_ANSWERED':
    'Câu trả lời đã được lưu. Đang tải câu tiếp theo…',
  'error.INVALID_ANSWER_SELECTION': 'Hãy chọn câu trả lời hợp lệ rồi thử lại.',
  'login.eyebrow': 'Chào mừng trở lại',
  'login.title': 'Đăng nhập',
  'login.lede': 'Tiếp tục lộ trình học tập được cá nhân hóa của bạn.',
  'login.pending': 'Đang đăng nhập…',
  'login.new': 'Bạn mới dùng SkillPath?',
  'login.create': 'Tạo tài khoản',
  'register.eyebrow': 'Bắt đầu lộ trình',
  'register.title': 'Tạo tài khoản',
  'register.lede': 'Cho chúng tôi biết về bạn. Tiếp theo là mục tiêu đầu tiên.',
  'register.name': 'Tên hiển thị',
  'register.pending': 'Đang tạo tài khoản…',
  'register.submit': 'Tạo tài khoản',
  'register.existing': 'Bạn đã có tài khoản?',
  'validation.email': 'Nhập địa chỉ email hợp lệ.',
  'validation.passwordRequired': 'Nhập mật khẩu.',
  'validation.passwordLength': 'Sử dụng ít nhất 12 ký tự.',
  'validation.name': 'Nhập tên của bạn.',
  'validation.timezoneRequired': 'Múi giờ là bắt buộc.',
  'validation.goal': 'Chọn một mục tiêu.',
  'validation.targetDate': 'Chọn ngày mục tiêu.',
  'goalSetup.loading': 'Đang tải các lộ trình học…',
  'goalSetup.empty': 'Chưa có lộ trình học nào.',
  'goalSetup.eyebrow': 'Chọn đích đến',
  'goalSetup.title': 'Tạo mục tiêu đầu tiên',
  'goalSetup.lede':
    'Chúng tôi sẽ dùng thời hạn và quỹ thời gian hằng ngày để tính lộ trình học ngắn nhất có cơ sở.',
  'goalSetup.track': 'Lộ trình nghề nghiệp tuyển chọn',
  'goalSetup.select': 'Chọn một lộ trình',
  'goalSetup.date': 'Ngày dự kiến sẵn sàng',
  'goalSetup.budget': 'Quỹ thời gian học mỗi ngày',
  'goalSetup.quickMinutes': 'Chọn nhanh số phút học mỗi ngày',
  'goalSetup.minutes': '{minutes} phút',
  'goalSetup.pending': 'Đang tính lộ trình…',
  'goalSetup.submit': 'Tạo lộ trình học',
  'goal.loading': 'Đang tính lộ trình học tối ưu…',
  'goal.noRoute': 'Chưa có lộ trình đang hoạt động',
  'goal.noGoal': 'Chưa có mục tiêu đang hoạt động',
  'goal.noGoalDetail':
    'Lộ trình bắt đầu sau khi bạn chọn đích đến và quỹ thời gian học mỗi ngày.',
  'goal.chooseTrack': 'Chọn lộ trình học',
  'goal.commandCenter': 'Trung tâm học tập hôm nay',
  'goal.welcome': 'Chào {name}',
  'goal.routeReady':
    'Lộ trình sẵn sàng thực tập Java Backend của bạn đã được thiết lập.',
  'goal.signingOut': 'Đang đăng xuất…',
  'goal.signOut': 'Đăng xuất',
  'goal.category': 'Đánh giá • Mốc bằng chứng ban đầu',
  'goal.durationLabel': 'Thời lượng dự kiến {minutes} phút',
  'goal.allocated': '⏱️ Hôm nay đã dành {minutes} phút',
  'goal.diagnosticTitle': 'Bài đánh giá Java Backend ban đầu',
  'goal.why': 'Vì sao có nhiệm vụ này:',
  'goal.rationale':
    'Tám câu hỏi khách quan ngắn sẽ tạo bằng chứng theo từng khái niệm cho giai đoạn Trạng thái Kiến thức tiếp theo. Bằng chứng mô tả điều mỗi lần làm thể hiện, không phải kết luận mức độ thành thạo.',
  'goal.questions': '8 câu hỏi',
  'goal.duration': '6–8 phút',
  'goal.resumeSafe': 'Có thể tiếp tục sau',
  'goal.preparing': 'Đang chuẩn bị bài đánh giá…',
  'goal.start': 'Bắt đầu hoặc tiếp tục đánh giá',
  'goal.retention':
    'Câu trả lời được lưu an toàn và có thể tiếp tục trong vòng bảy ngày.',
  'goal.specifications': 'Thông tin mục tiêu đang hoạt động',
  'goal.targetDate': 'Ngày mục tiêu',
  'goal.dailyBudget': 'Quỹ thời gian mỗi ngày',
  'goal.perDay': '{minutes} phút / ngày',
  'goal.status': 'Trạng thái',
  'goal.active': 'Đang hoạt động',
  'goal.milestone': 'Mốc hiện tại:',
  'goal.milestoneDetail':
    'Hoàn thành bài đánh giá để tạo bằng chứng quan sát mà Giai đoạn 4 sẽ chuyển thành Trạng thái Kiến thức.',
  'auth.session': 'Phiên đăng nhập',
  'auth.ended': 'Phiên đã kết thúc',
  'auth.endedDetail': 'Đăng nhập lại để tiếp tục lộ trình học tập.',
  'diagnostic.eyebrow': 'Đánh giá đầu vào',
  'diagnostic.noSession': 'Chưa chọn phiên đánh giá',
  'diagnostic.noSessionDetail':
    'Bắt đầu hoặc tiếp tục bài đánh giá từ mục tiêu đang hoạt động.',
  'diagnostic.loadingQuestion': 'Đang tải câu hỏi tiếp theo…',
  'diagnostic.loadingResult': 'Đang chuẩn bị bản tóm tắt bằng chứng…',
  'diagnostic.noResult': 'Chưa có kết quả đánh giá.',
  'diagnostic.title': 'Đánh giá Java Backend',
  'diagnostic.progress': 'Câu {position} / {total}',
  'diagnostic.aboutMinutes': 'Khoảng {minutes} phút',
  'diagnostic.progressLabel': 'Tiến độ bài đánh giá',
  'diagnostic.selectMany': 'Chọn tất cả đáp án phù hợp.',
  'diagnostic.selectOne': 'Chọn một đáp án.',
  'diagnostic.confidence': 'Bạn tự tin đến mức nào về câu trả lời này?',
  'diagnostic.confidenceLow': 'Chưa tự tin lắm',
  'diagnostic.confidenceMedium': 'Khá tự tin',
  'diagnostic.confidenceHigh': 'Rất tự tin',
  'diagnostic.leave': 'Lưu và rời đi',
  'diagnostic.saving': 'Đang lưu câu trả lời…',
  'diagnostic.submit': 'Gửi và tiếp tục',
  'diagnostic.boundary':
    'Bài đánh giá ghi nhận bằng chứng từ mỗi lần làm. Nó không tuyên bố mức độ thành thạo hay chọn nhiệm vụ học tiếp theo.',
  'result.complete': 'Đã hoàn thành đánh giá',
  'result.title': 'Đã ghi nhận mốc bằng chứng ban đầu',
  'result.scoreLabel': 'Điểm trả lời khách quan quan sát được',
  'result.score': 'Điểm trả lời quan sát được',
  'result.notMastery': 'Không phải điểm thành thạo hay mức độ sẵn sàng',
  'result.demonstrated': 'Những điều các lần làm đã thể hiện',
  'result.observed': 'Quan sát được {score}%',
  'result.dimension.RECOGNITION': 'nhận biết',
  'result.dimension.UNDERSTANDING': 'thấu hiểu',
  'diagnostic.expired': 'Bài đánh giá đã hết hạn',
  'diagnostic.expiredTitle': 'Phiên đánh giá này đã hết hạn',
  'diagnostic.expiredDetail':
    'Quay lại mục tiêu đang hoạt động để bắt đầu một phiên đánh giá mới.',
  'knowledge.open': 'Xem trạng thái kiến thức',
  'knowledge.loading': 'Đang xây dựng trạng thái kiến thức…',
  'knowledge.eyebrow': 'Trạng thái kiến thức',
  'knowledge.title': 'SkillPath hiện ước tính bạn biết gì',
  'knowledge.lede':
    'Phép chiếu này kết hợp bằng chứng quan sát và thay đổi khi bằng chứng cũ đi.',
  'knowledge.empty': 'Chưa có trạng thái kiến thức',
  'knowledge.emptyDetail':
    'Hoàn thành bài đánh giá rồi chờ trong giây lát để hệ thống xử lý bằng chứng.',
  'knowledge.mastery': 'Mức thành thạo ước tính',
  'knowledge.confidence': 'Độ tin cậy',
  'knowledge.evidence': '{count} bằng chứng quan sát',
  'knowledge.boundary':
    'Trạng thái kiến thức ước tính điều bạn biết. Lập kế hoạch cá nhân hóa bắt đầu ở Giai đoạn 5.',
  'knowledge.status.UNKNOWN': 'Chưa rõ',
  'knowledge.status.LEARNING': 'Đang học',
  'knowledge.status.PROVISIONAL': 'Tạm thời',
  'knowledge.status.MASTERED': 'Đã thành thạo',
  'knowledge.status.REVIEW_DUE': 'Đến hạn ôn tập',
}

interface I18nValue {
  locale: AppLocale
  setLocale: (locale: AppLocale) => void
  t: (key: TranslationKey, replacements?: Replacements) => string
}

const I18nContext = createContext<I18nValue | undefined>(undefined)

export function I18nProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [locale, setLocaleState] = useState<AppLocale>(getPreferredLocale)

  useEffect(() => {
    document.documentElement.lang = locale
  }, [locale])

  const setLocale = useCallback(
    (nextLocale: AppLocale) => {
      window.localStorage.setItem(LOCALE_STORAGE_KEY, nextLocale)
      document.documentElement.lang = nextLocale
      setLocaleState(nextLocale)
      void queryClient.invalidateQueries()
    },
    [queryClient],
  )

  const value = useMemo<I18nValue>(() => {
    const dictionary = locale === 'vi-VN' ? vi : en
    return {
      locale,
      setLocale,
      t: (key, replacements = {}) =>
        Object.entries(replacements).reduce(
          (text, [name, replacement]) =>
            text.replaceAll(`{${name}}`, String(replacement)),
          dictionary[key],
        ),
    }
  }, [locale, setLocale])

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>
}

export function useI18n() {
  const value = useContext(I18nContext)
  if (!value) throw new Error('useI18n must be used inside I18nProvider')
  return value
}

export function LanguageSelector() {
  const { locale, setLocale, t } = useI18n()
  return (
    <label className="language-selector">
      <span>{t('language.label')}</span>
      <select
        aria-label={t('language.label')}
        value={locale}
        onChange={(event) => setLocale(event.target.value as AppLocale)}
      >
        <option value="vi-VN">{t('language.vi')}</option>
        <option value="en">{t('language.en')}</option>
      </select>
    </label>
  )
}

export type { TranslationKey }
