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
    'Set your goal and daily budget. Personalized planning will arrive in a later phase.',
  'goalSetup.track': 'Curated career track',
  'goalSetup.select': 'Select a path',
  'goalSetup.date': 'Target readiness date',
  'goalSetup.budget': 'Daily study budget',
  'goalSetup.quickMinutes': 'Quick daily minutes selection',
  'goalSetup.minutes': '{minutes} minutes',
  'goalSetup.pending': 'Saving goal…',
  'goalSetup.submit': 'Save learning goal',
  'goal.loading': 'Loading your active goal…',
  'goal.noRoute': 'No active route',
  'goal.noGoal': 'No active goal yet',
  'goal.noGoalDetail':
    'Your route starts after you set a destination and daily study budget.',
  'goal.chooseTrack': 'Choose learning track',
  'goal.commandCenter': 'Today Command Center',
  'goal.welcome': 'Welcome, {name}',
  'goal.routeReady': 'Your Java Backend learning goal is active.',
  'goal.signingOut': 'Signing out…',
  'goal.signOut': 'Sign out',
  'goal.category': 'Diagnostic • Evidence baseline',
  'goal.durationLabel': 'Estimated duration {minutes} minutes',
  'goal.allocated': '⏱️ {minutes} min daily budget',
  'goal.diagnosticTitle': 'Java Backend initial diagnostic',
  'goal.why': 'Why this task:',
  'goal.rationale':
    'Eight short objective questions create concept-level evidence. Knowledge State estimates what you know; this diagnostic does not decide mastery.',
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
    'You can take the diagnostic, inspect Knowledge State, or choose a foundational study sequence. A personalized Today plan is not available yet.',
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
    'Knowledge State estimates what you know. Personalized planning arrives in Phase 6; completing a self-selected task is not mastery evidence.',
  'knowledge.status.UNKNOWN': 'Unknown',
  'knowledge.status.LEARNING': 'Learning',
  'knowledge.status.PROVISIONAL': 'Provisional',
  'knowledge.status.MASTERED': 'Mastered',
  'knowledge.status.REVIEW_DUE': 'Review due',
  'learning.open': 'Open self-selected study',
  'learning.eyebrow': 'Curated study',
  'learning.title': 'Choose a study sequence',
  'learning.intro':
    'Choose this foundation sequence yourself. It is not a personalized Today plan.',
  'learning.loading': 'Loading study sequences…',
  'learning.empty': 'No compatible study sequence is available yet.',
  'learning.noGoal': 'Create an active goal before studying.',
  'learning.minutes': '{minutes} minutes',
  'learning.start': 'Start this sequence',
  'learning.continue': 'Continue your study session',
  'learning.step': 'Step {position} of {total}',
  'learning.activity.LEARN': 'Learn',
  'learning.activity.PRACTICE': 'Practice',
  'learning.activity.RECALL': 'Recall',
  'learning.checklist': 'What I did',
  'learning.actualMinutes': 'Minutes spent',
  'learning.beginTask': 'Begin this step',
  'learning.completeTask': 'Mark step complete',
  'learning.skipTask': 'Skip and stop',
  'learning.blockTask': 'I am blocked',
  'learning.resumeTask': 'Resume this step',
  'learning.abandonTask': 'Abandon and stop',
  'learning.reason': 'Reason',
  'learning.reason.TIME': 'Not enough time',
  'learning.reason.DIFFICULT': 'This is difficult',
  'learning.reason.OTHER': 'Other',
  'learning.completed': 'Sequence completed',
  'learning.stopped': 'Session stopped',
  'learning.boundary':
    'This records your activity only. It does not grade your work, update mastery, or create an adaptive plan.',
  'learning.retry': 'Retry the same command',
  'learning.sync': 'Refresh session',
  'learning.missing': 'This study session was not found.',
  'learning.validation':
    'Complete every checklist item and enter minutes spent (0–360).',
  'learning.goCatalog': 'Choose another sequence',
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
    'Đặt mục tiêu và quỹ thời gian mỗi ngày. Kế hoạch cá nhân hóa sẽ có ở giai đoạn sau.',
  'goalSetup.track': 'Lộ trình nghề nghiệp tuyển chọn',
  'goalSetup.select': 'Chọn một lộ trình',
  'goalSetup.date': 'Ngày dự kiến sẵn sàng',
  'goalSetup.budget': 'Quỹ thời gian học mỗi ngày',
  'goalSetup.quickMinutes': 'Chọn nhanh số phút học mỗi ngày',
  'goalSetup.minutes': '{minutes} phút',
  'goalSetup.pending': 'Đang lưu mục tiêu…',
  'goalSetup.submit': 'Lưu mục tiêu học',
  'goal.loading': 'Đang tải mục tiêu đang hoạt động…',
  'goal.noRoute': 'Chưa có lộ trình đang hoạt động',
  'goal.noGoal': 'Chưa có mục tiêu đang hoạt động',
  'goal.noGoalDetail':
    'Lộ trình bắt đầu sau khi bạn chọn đích đến và quỹ thời gian học mỗi ngày.',
  'goal.chooseTrack': 'Chọn lộ trình học',
  'goal.commandCenter': 'Trung tâm học tập hôm nay',
  'goal.welcome': 'Chào {name}',
  'goal.routeReady': 'Mục tiêu học Java Backend của bạn đang hoạt động.',
  'goal.signingOut': 'Đang đăng xuất…',
  'goal.signOut': 'Đăng xuất',
  'goal.category': 'Đánh giá • Mốc bằng chứng ban đầu',
  'goal.durationLabel': 'Thời lượng dự kiến {minutes} phút',
  'goal.allocated': '⏱️ Quỹ thời gian {minutes} phút mỗi ngày',
  'goal.diagnosticTitle': 'Bài đánh giá Java Backend ban đầu',
  'goal.why': 'Vì sao có nhiệm vụ này:',
  'goal.rationale':
    'Tám câu hỏi khách quan ngắn tạo bằng chứng theo từng khái niệm. Trạng thái Kiến thức ước tính điều bạn biết; bài này không tự kết luận mức độ thành thạo.',
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
    'Bạn có thể làm bài đánh giá, xem Trạng thái Kiến thức hoặc tự chọn chuỗi học nền tảng. Kế hoạch Hôm nay cá nhân hóa chưa có.',
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
    'Trạng thái Kiến thức ước tính điều bạn biết. Kế hoạch cá nhân hóa thuộc Giai đoạn 6; tự đánh dấu hoàn thành bài học không phải bằng chứng thành thạo.',
  'knowledge.status.UNKNOWN': 'Chưa rõ',
  'knowledge.status.LEARNING': 'Đang học',
  'knowledge.status.PROVISIONAL': 'Tạm thời',
  'knowledge.status.MASTERED': 'Đã thành thạo',
  'knowledge.status.REVIEW_DUE': 'Đến hạn ôn tập',
  'learning.open': 'Mở phần tự chọn học',
  'learning.eyebrow': 'Nội dung học chọn lọc',
  'learning.title': 'Chọn chuỗi học',
  'learning.intro':
    'Bạn tự chọn chuỗi nền tảng này. Đây chưa phải kế hoạch Hôm nay được cá nhân hóa.',
  'learning.loading': 'Đang tải chuỗi học…',
  'learning.empty': 'Chưa có chuỗi học phù hợp.',
  'learning.noGoal': 'Hãy tạo mục tiêu đang hoạt động trước khi học.',
  'learning.minutes': '{minutes} phút',
  'learning.start': 'Bắt đầu chuỗi học',
  'learning.continue': 'Tiếp tục phiên học',
  'learning.step': 'Bước {position} / {total}',
  'learning.activity.LEARN': 'Học',
  'learning.activity.PRACTICE': 'Thực hành',
  'learning.activity.RECALL': 'Nhớ lại',
  'learning.checklist': 'Những việc tôi đã làm',
  'learning.actualMinutes': 'Số phút đã học',
  'learning.beginTask': 'Bắt đầu bước này',
  'learning.completeTask': 'Đánh dấu hoàn thành',
  'learning.skipTask': 'Bỏ qua và dừng',
  'learning.blockTask': 'Tôi đang gặp khó khăn',
  'learning.resumeTask': 'Tiếp tục bước này',
  'learning.abandonTask': 'Dừng và bỏ bước',
  'learning.reason': 'Lý do',
  'learning.reason.TIME': 'Không đủ thời gian',
  'learning.reason.DIFFICULT': 'Bài này khó',
  'learning.reason.OTHER': 'Lý do khác',
  'learning.completed': 'Đã hoàn thành chuỗi học',
  'learning.stopped': 'Phiên học đã dừng',
  'learning.boundary':
    'Chỉ ghi nhận hoạt động của bạn. Hệ thống chưa chấm bài, cập nhật mức thành thạo hoặc tạo kế hoạch thích ứng từ việc đánh dấu hoàn thành.',
  'learning.retry': 'Thử lại cùng lệnh',
  'learning.sync': 'Tải lại phiên học',
  'learning.missing': 'Không tìm thấy phiên học này.',
  'learning.validation':
    'Hãy đánh dấu đủ các mục và nhập số phút đã học (0–360).',
  'learning.goCatalog': 'Chọn chuỗi học khác',
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
