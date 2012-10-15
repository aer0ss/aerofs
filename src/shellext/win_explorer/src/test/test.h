#ifndef TEST_H
#define TEST_H

/**
 * Ultra-lightweight unit testing and benchmarking, inspired by QTestLib
 *
 * Core design goals:
 *   - ease of use
 *   - fit in a single small header file
 *   - portability (only requires STL, exceptions and RTTI)
 */

#if defined(__APPLE__)
  #if defined(__x86_64__)
    #include <sys/sysctl.h>
  #else
    #include <mach/mach.h>
    #include <mach/mach_time.h>
  #endif // __x86_64__ or not
#elif defined(_WIN32)
  #include <windows.h>
  #include <time.h>
  #define PSAPI_VERSION 1
  #include <psapi.h>
#else
  #include <stdio.h>
  #include <stdlib.h>
  #include <string.h>
  #include <sys/time.h>
#endif

#include <string>
#include <sstream>
#include <list>
#include <map>
#include <vector>

/**
 * Utility functions for benchmarking
 */
class Util {
public:
	typedef unsigned long long SysClock;

	struct MemInfo {
		size_t virtualSize;
		size_t residentSize;

		inline MemInfo()
		: virtualSize(0), residentSize(0) {}
	};

	/**
	 * Get process memory usage
	 */
	static MemInfo memInfo() {
		MemInfo info;
#if defined(__APPLE__)
		struct task_basic_info t_info;
		mach_msg_type_number_t t_info_count = TASK_BASIC_INFO_COUNT;

		if (KERN_SUCCESS == task_info(mach_task_self(),
									  TASK_BASIC_INFO, (task_info_t)&t_info,
									  &t_info_count))
		{
			info.residentSize = t_info.resident_size;
			info.virtualSize = t_info.virtual_size;
		}
#elif defined(_WIN32)
		PROCESS_MEMORY_COUNTERS_EX pmc;
		HANDLE pHandle = GetCurrentProcess();
		if (GetProcessMemoryInfo(pHandle, reinterpret_cast<PPROCESS_MEMORY_COUNTERS>(&pmc), sizeof(pmc))) {
			info.virtualSize = pmc.PagefileUsage ? pmc.PagefileUsage : pmc.PrivateUsage;
			info.residentSize = pmc.WorkingSetSize;
		}
		CloseHandle(pHandle);
#else
		// assume unix and parse /proc/self/status
		FILE* f = fopen("/proc/self/status", "r");
		// TODO: look for VmRss and VmSize lines and convert the strings to int
		#warning "memInfo not implemented, banchmarks will not report accurate memory usage"
		fclose(f);
#endif
		return info;
	}

	/**
	 * Get number of ticks since an arbitrary origin
	 */
	static SysClock ticks() {
#if defined(__APPLE__) && !defined(__x86_64__)
      return mach_absolute_time();
#elif defined(_WIN32)
      LARGE_INTEGER qwTime;
      QueryPerformanceCounter(&qwTime);
      return qwTime.QuadPart;
#elif defined(__x86_64__)
      unsigned int a, d;
      asm volatile("rdtsc" : "=a" (a), "=d" (d));
      return static_cast<unsigned long long>(a) |
        (static_cast<unsigned long long>(d) << 32);
#elif defined(__ARM_NEON__) && 0 // mrc requires superuser.
      unsigned int val;
      asm volatile("mrc p15, 0, %0, c9, c13, 0" : "=r"(val));
      return val;
#else
      timespec spec;
      clock_gettime(CLOCK_THREAD_CPUTIME_ID, &spec);
      return SysClock(static_cast<float>(spec.tv_sec) * 1e9 + static_cast<float>(spec.tv_nsec));
#endif
	}

	/**
	 * Unit of the value returned by ticks()
	 * Clock cycles (cc) or nanoseconds (ns)
	 */
	static const char* tickUnits() {
#if defined(__APPLE__) && !defined(__x86_64__)
      return "ns";
#elif defined(_WIN32) || defined(__x86_64__)
      return "cc";
#else
      return "ns"; // clock_gettime
#endif
    }
};

/**
 * Helper macros for debug output
 */
#define STRINGIFY_(x) #x
#define STRINGIFY(x) STRINGIFY_(x)
#define LOCATION __FILE__ ":" STRINGIFY(__LINE__)

#define SSTR(streamed) (dynamic_cast<const std::stringstream&>((std::stringstream() << streamed))).str()
#define WSTR(streamed) (dynamic_cast<const std::wstringstream&>((std::wstringstream() << streamed))).str()

/**
 * On Windows, assume the test will be run through a debugger and use OutputDebugString
 */
#if defined(_WIN32)
#define DOUT_RAW(str) OutputDebugStringA(str)
#define WOUT_RAW(str) OutputDebugStringW(str)
#else
#define DOUT_RAW(str) fputs(str, stderr);
#define WOUT_RAW(str) // TODO...
#endif

#define DOUT(streamed) DOUT_RAW(SSTR(streamed).c_str())
#define WOUT(streamed) WOUT_RAW(WSTR(streamed).c_str())

#ifdef _MSC_VER
/**
 * On MSVC use Structured Exception Handling to catch SEGV and FPE
 */
#define USE_SEH
#endif

/**
 * Helper class to create test cases and register them to easily run them all
 */
class Test {
	enum State {
		Pass,
		Fail,
		Error,
		StateCount
	};

#if defined(USE_SEH)
	enum {
		EXCEPTION_TEST_FAIL = 0xfa11
	};

	void printException(LPEXCEPTION_RECORD er) {
		// TODO: display stack trace
		DOUT("Uncaught exception " << er->ExceptionCode << " at " << er->ExceptionAddress << "\n");
	}
#else
	class TestFailException {};
#endif

public:
	/**
	 * Run all registered test cases
	 */
	static int runAll() {
		int counter[StateCount] = {0, 0, 0};
		static const char* msg[StateCount] = {"PASSED", "FAILED", "ERROR"};

		std::list<Test*>::const_iterator
			it = tests().cbegin(),
			end = tests().cend();
		while (it != end) {
			DOUT((*it)->m_context << " RUNNING\n");
			int ret = (*it)->safeRun();
			DOUT((*it)->m_context << " " << msg[ret] << "\n");
			++counter[ret];
			++it;
		}

		DOUT("Pass: " << counter[Pass]
		<< "  Fail: " << counter[Fail]
		<< "  Error: " << counter[Error] << "\n");

		return counter[Fail] + counter[Error] > 0 ? EXIT_FAILURE : EXIT_SUCCESS;
	}

	static inline void fail() {
#if defined(USE_SEH)
		RaiseException(EXCEPTION_TEST_FAIL, 0, 0, NULL);
#else
		throw TestFailException();
#endif
	}

protected:
	Test() {}
	virtual ~Test() {}

	void registerTest() { tests().push_back(this); }
	void setContext(const std::string& context) { m_context = context; }

	/**
	 * Wrap test case in exception handlers to catch test failures
	 */
	int safeRun() {
#if defined(USE_SEH)
		LPEXCEPTION_POINTERS ep = 0;
		__try {
			setUp();
			__try {
				run();
			} __finally {
				tearDown();
			}
			return Pass;
		} __except (ep = GetExceptionInformation(), EXCEPTION_EXECUTE_HANDLER) {
			LPEXCEPTION_RECORD er = ep->ExceptionRecord;
			if (er->ExceptionCode == EXCEPTION_TEST_FAIL) {
				return Fail;
			} else {
				printException(er);
				return Error;
			}
		}
#else
		// TODO: signal handler to catch segv and fpe
		try {
			setUp();
			try {
				run();
				tearDown();
			} catch (...) {
				tearDown();
				throw;
			}
			return Pass;
		} catch (const TestFailException& e) {
			return Fail;
		} catch (...) {
			// TODO: display stack trace
			return Error;
		}
#endif
	}

	/**
	 * Actual test case
	 */
	virtual void run() {}

	/**
	 * Fixture support
	 */
	virtual void setUp() {}
	virtual void tearDown() {}

	inline static size_t testCount() { return tests().size(); }

	std::string m_context;

private:
	static std::list<Test*>& tests() {
		static std::list<Test*> _l;
		return _l;
	}
};

/**
 * Helper to notify of test failure
 *
 * FAIL("something went wrong");
 */
#define FAIL_(streamed) do { \
	DOUT(streamed);          \
	Test::fail();            \
} while (0)

#define FAIL(streamed) FAIL_(LOCATION ": " << streamed << "\n")

// factored out test creation (used by regular and fixture based tests)
#define TEST_(context, base, name)                              \
struct test_##context##_##name : public base {                  \
	test_##context##_##name()                                   \
	{ setContext("[" #context "." #name "]"); registerTest(); } \
	virtual void run();                                         \
	static test_##context##_##name m_instance;                  \
};                                                              \
test_##context##_##name test_##context##_##name::m_instance;    \
void test_##context##_##name::run()

/**
 * Test case creation macro:
 *
 * TEST(Foo, bar) {
 *     // setup here..
 *     VERIFY(baz);
 *     // some interaction here...
 *     // more checks after interaction...
 * }
 *
 * First param: context, usually (but not necessarily) a class name
 * Second param: name of the test case
 */
#define TEST(context, name) TEST_(context, Test, name)

/**
 * Test fixture creation macro
 *
 * The purpose of test fixture is to avoid duplication of setup and cleanup
 * code across multiple test cases.
 *
 * class MyFixture : public Test {
 * public:
 *     virtual void setUp() {
 *         //shared setup here
 *     }
 *     virtual void tearDown() {
 *         //shared cleanup here
 *     }
 * };
 *
 * TEST_F(MyFixture, foo) {
 * }
 *
 * TEST_F(MyFixture, bar) {
 * }
 *
 * First param: fixture (it is also used as context, see the basic TEST macro)
 * Second param: name of the test case
 */
#define TEST_F(fixture, name) TEST_(fixture, fixture, name)

/**
 * Type-safe arbitrary parameter passing is the most involved part of this framework.
 *    - must not leak memory allocated by the parameters
 *    - must detect type mismatch between declaration, instanciation and fetch
 *
 * A very simple metatype-like system is used to assign a unique id to each type being used
 * as a test parameter. The major limitation of this approach is that it does not allow
 * implicit casts: the exact type used for column declaration must be used for instanciation
 * and fetch.
 */

/**
 * Abstract base class of wrapper parameters
 */
struct ParamBase {
	virtual ~ParamBase() {}
	virtual int id() const = 0;
	inline const char* name() const { return name(id()); }
	static const char* name(int id) { return names().at(id); }
protected:
	static int id(const char *name) { static int _i = 0; names().push_back(name); return _i++; }
private:
	static std::vector<const char*>& names() { static std::vector<const char*> _l; return _l; }
};

/**
 * Concrete parameter wrapping
 */
template <typename T> struct TypedParam : public ParamBase {
	TypedParam(const T& v) : value(v) {}
	virtual ~TypedParam() {}
	virtual int id() const { return type_id(); }

	T value;
	static int type_id() { static int _tid = ParamBase::id(typeid(T).name());  return _tid; }
};

/**
 * Parameter declaration: name->index and index->type
 */
class ParamKeys {
public:
	int index(const std::string& k) {
		std::map<std::string, int>::const_iterator it = m_keys.find(k);
		return it != m_keys.cend() ? it->second : -1;
	}

	template <typename T> void add(const std::string& k) {
		if (m_keys.count(k)) return;
		m_keys[k] = (int)m_keys.size();
		m_types.push_back(TypedParam<T>::type_id());
	}

	std::vector<int> m_types;
	std::map<std::string, int> m_keys;
};

/**
 * Parameter instanciation
 */
class ParamValues {
public:
	ParamValues(std::vector<int>& types) : m_types(types) {}

	template <typename T> const T& get(int idx, const char* location) {
		ParamBase *p = m_values.at(idx);
		if (p->id() != TypedParam<T>::type_id()) {
			FAIL_(location << ": FETCH " << typeid(T).name() << " from " << p->name() << " column\n");
		}
		return static_cast<TypedParam<T>*>(p)->value;
	}

	#define DELAYED_FAIL(streamed) \
	do { m_errors.append(SSTR("  " << streamed << "\n")); return *this; } while (0)

	template <typename T> ParamValues& operator << (const T& v) {
		if (m_values.size() == m_types.size())
			DELAYED_FAIL("Extra " << typeid(T).name() <<  " parameter");
		// TODO: allow implicit type casts...
		int tid = m_types.at(m_values.size());
		m_values.push_back(new TypedParam<T>(v));
		if (tid != TypedParam<T>::type_id()) {
			DELAYED_FAIL("Passed " << typeid(T).name() << " to column " << m_values.size()
				<< " [expected " << ParamBase::name(tid) << "]");
		}
		return *this;
	}

	#undef DELAYED_FAIL

	inline void verify(const std::string& context) {
		if (m_values.size() != m_types.size()) {
			m_errors.append(
				SSTR("  Missing " << (m_types.size() - m_values.size()) << " parameter(s)\n"));
		}
		if (m_errors.size()) FAIL_(context << " Invalid parameters:\n" << m_errors);
	}

	inline void clear() {
		for (int i = 0; i < (int)m_values.size(); ++i) delete m_values.at(i);
		m_values.clear();
	}

private:
	std::string m_errors;
	std::vector<int>& m_types;
	std::vector<ParamBase*> m_values;
};

/**
 * Parametrized tests, QTestLib-like syntax
 *
 * TEST_P(Foo, bar) {
 *     FETCH(int, n);
 *     VERIFY(n == 10);
 *     // alternatively : VERIFY(PARAM(n) == 10);
 * }
 *
 * TEST_DATA(Foo, bar) {
 *     addColumn<int>("n");
 *     newRow("n = 10") << 10;
 *     newRow("n = 20") << 20;
 * }
 *
 * NB: Fixture can be parametrized too
 *
 * TEST_P_F(MyFixture, bar) {
 *     // ...
 * }
 * TEST_DATA(MyFixture, bar) {
 *     // ...
 * }
 */
#define TEST_P_(context, base, name)                                    \
struct test_##context##_##name : public base {                          \
	typedef test_##context##_##name self_t;                             \
	test_##context##_##name(const std::string& inst)                    \
	: m_values(keys().m_types)                                          \
	{ setContext(SSTR("[" #context "." #name "(" << inst << ")]")); }   \
	virtual void run();                                                 \
	virtual void setUp() { m_values.verify(m_context); base::setUp(); } \
	virtual void tearDown() { m_values.clear(); base::tearDown(); }     \
	template <typename T> inline static void addColumn(const char* k)   \
	{ keys().add<T>(k); }			                                    \
	inline static ParamValues& newRow(const std::string& inst) {        \
		instances().push_back(self_t(inst));                            \
		instances().back().registerTest();                              \
		return instances().back().m_values;                             \
	}                                                                   \
private:                                                                \
	static ParamKeys& keys() { static ParamKeys _k; return _k; }        \
	static std::list<self_t>& instances()                               \
	{ static std::list<self_t> _l; return _l; }                         \
	ParamValues m_values;                                               \
	static size_t m_initialized;                                        \
	static size_t test_forceInit()                                      \
    { size_t n = testCount(); test_initData(); return testCount()-n; }  \
	static void test_initData();                                        \
};                                                                      \
size_t test_##context##_##name::m_initialized = test_forceInit();       \
void test_##context##_##name::run()

#define TEST_P(context, name) TEST_P_(context, Test, name)
#define TEST_P_F(fixture, name) TEST_P_(fixture, fixture, name)

#define TEST_DATA(context, name) void test_##context##_##name::test_initData()

#define FETCH(T, name) T name = m_values.get<T>(keys().index(#name), LOCATION)

/**
 * Test verification macros:
 *
 * VERIFY(foo == bar);
 * VERIFY_MSG(foo == bar, "foo is not equal to bar");
 *
 * COMPARE(expected, actual);
 * COMPARE_MSG(expected, actual, "actual is different from expected");
 *
 * COMPARE_T(baseType, expected, actual);
 * COMPARE_T_MSG(baseType, expected, actual, "actual is different from expected");
 */

#define VERIFY(boolean_expr) do {                              \
	if (!(boolean_expr)) FAIL("check failed: " #boolean_expr); \
} while (0)

#define VERIFY_MSG(boolean_expr, msg) do {                     \
	if (!boolean_expr) FAIL(msg);                              \
} while (0)

/**
 * Helper class to print difference between expected and actual values in failed equality checks
 */
template <typename T> struct Differ {
	static inline std::string diff(const T& expected, const T& actual) {
		return SSTR("\n  expected: " << expected << "\n  actual: " << actual << "\n");
	}
};

/**
 * Helper function to perform equality checks
 */
template <typename T> inline void check_eq(const T& expected, const T& actual,
	const std::string& fail_msg) {
	if (!(expected == actual)) {
		DOUT(fail_msg << Differ<T>::diff(expected, actual));
		Test::fail();
	}
}

#define COMPARE(expected, actual) \
	check_eq(expected, actual, LOCATION " check failed: ")
#define COMPARE_MSG(expected, actual, msg) \
	check_eq(expected, actual, LOCATION " check failed: ")
#define COMPARE_T(T, expected, actual) \
	check_eq<T>(expected, actual, SSTR(LOCATION << msg))
#define COMPARE_T_MSG(T, expected, actual, msg) \
	check_eq<T>(expected, actual, SSTR(LOCATION << msg))

/**
 * Benchmarking helper
 * Track cycle count and evolution of memory usage during its lifetime
 */
class BenchmarkHelper {
	int m_iter;
	Util::MemInfo mRef;
	Util::SysClock cRef;

public:
	BenchmarkHelper(const std::string& cxt) {
		m_iter = 0;
		DOUT(cxt << "\n");
		mRef = Util::memInfo();
		cRef = Util::ticks();
	}

	~BenchmarkHelper() {
		Util::SysClock cAfter = Util::ticks();
		Util::MemInfo mAfter = Util::memInfo();

		// TODO: print thousand separators or scale units for readability
		// NOTE: when doing multiple subsequent benchmarks, memory usage accuracy
		// is limited by the tendency of the OS/libc to not release freed memory
		// immediately.
		DOUT("  >>>> "
			<< (cAfter - cRef) << Util::tickUnits() << " "
			<< (mAfter.virtualSize - mRef.virtualSize) << "b "
			<< (mAfter.residentSize - mRef.residentSize) << "b\n"
			);
	}

	bool hasNext() {
		// TODO: auto-adjust number of iterations to get stable timing results
		return m_iter < 1;
	}

	void next() {
		++m_iter;
	}
};

/**
 * Benchmark creation macro
 *
 * Track cycle count and evolution of memory usage for a block of code:
 *
 * BENCHMARK(foo) {
 *     doSomethingExpensive();
 *     doSomethingMore();
 * }
 *
 * BENCHMARK(bar) doSomethingExpensive();
 *
 * NB: this macro can only be used *inside* a function:
 * // GOOD:
 * TEST(Foo, bar) {
 *     BENCHMARK(bar) baz();
 * }
 *
 * // BAD:
 * BENCHMARK(bar) baz();
 */
#define BENCHMARK(cxt) for (BenchmarkHelper h(SSTR(cxt)); h.hasNext(); h.next())

/**
 * Convenience macro to run all tests
 */
#define RUN_ALL_TESTS() Test::runAll()

#endif