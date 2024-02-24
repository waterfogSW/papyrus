
분산 시스템의 안정성과 일관성을 보장하기 위해, 분산락(distributed lock)은 필수적인 메커니즘 중 하나입니다. Spring 프레임워크에서 분산락 구현은 주로 Aspect-Oriented Programming(AOP)를 통해 이루어집니다. 그러나 Spring AOP를 활용할 경우, 몇 가지 제약 사항과 단점이 존재합니다.

## Spring AOP의 한계

1. **Pointcut 표현식 사용**: Spring AOP는 pointcut 표현식을 통해 어드바이스(Advice) 적용 대상을 지정합니다. 이 표현식을 정확히 작성하는 것은 복잡하고, 오류가 발생하기 쉬운 작업입니다.
    
2. **적용 여부 확인**: Spring AOP를 적용한 후, 해당 AOP가 정상적으로 적용되었는지 런타임에서만 확인할 수 있습니다. 이는 개발 과정에서 시간을 소모하게 만듭니다.
    
3. **내부 메서드 적용 불가**: 클래스 내부에서 호출되는 private 메서드에는 Spring AOP가 적용되지 않습니다. 이는 내부 로직에 분산락을 적용하려 할 때 문제가 됩니다.
    
4. **SpEL 사용의 복잡성**: 분산락의 키값을 지정하기 위해 Spring Expression Language(SpEL)를 사용하게 되는데, 이는 컴파일 타임에서는 오류를 확인할 수 없으며, 잘못된 값이 지정되면 런타임 예외를 발생시킵니다.

## Kotlin Trailing Lambdas

이러한 Spring AOP의 한계를 극복하기 위해, Kotlin의 Trailing Lambdas를 활용한 방식을 도입할 수 있습니다. Kotlin에서 함수는 일급 객체이며, Trailing Lambdas를 사용하면, 함수를 더 직관적이고 유연하게 다룰 수 있습니다. 이를 통해 분산락 구현에 있어 AOP의 한계를 해결할 수 있습니다.

Kotlin에서 Trailing Lambdas는 함수형 프로그래밍의 강력한 특성 중 하나입니다. 이 개념을 이해하기 위해서는 먼저 Kotlin에서의 람다식과 고차 함수에 대한 이해가 필요합니다.

### 람다식(Lambda Expressions)

람다식은 간단히 말해 익명 함수입니다. 이는 함수를 간결하게 표현할 수 있게 해 주며, 다른 함수의 인자로 전달되거나 변수에 저장될 수 있습니다. Kotlin에서 람다식은 `{ }`로 둘러싸여 표현됩니다. 예를 들어, 다음은 두 수의 합을 반환하는 람다식입니다:

```kotlin
val sum: (Int, Int) -> Int = { x, y -> x + y }
```

### 고차 함수(Higher-Order Functions)

고차 함수는 다른 함수를 인자로 받거나 함수를 결과로 반환하는 함수를 말합니다. Kotlin에서 함수는 일급 객체이므로, 변수에 할당될 수 있고 다른 함수의 인자나 반환 값으로 사용될 수 있습니다. 예를 들어, 다음 함수 `calculate`는 함수를 인자로 받고, 두 개의 정수와 함께 이 함수를 호출합니다:

```kotlin
fun calculate(x: Int, y: Int, operation: (Int, Int) -> Int): Int {
	return operation(x, y) 
}
```

여기서 `operation` 파라미터는 람다식을 받는 고차 함수의 예입니다.

### Trailing Lambdas

Kotlin에서는 함수의 마지막 인자가 람다식인 경우, 람다식을 괄호 밖으로 빼내어 코드의 가독성을 높일 수 있습니다. 이를 Trailing Lambdas라고 합니다. 예를 들어, 위의 `calculate` 함수를 호출할 때, 다음과 같이 Trailing Lambdas를 사용할 수 있습니다:

```kotlin
val result = calculate(10, 20) { a, b -> a + b }
```

여기서 `{ a, b -> a + b }`는 `calculate` 함수의 마지막 인자로 전달된 람다식입니다. 이 문법을 사용함으로써, 코드가 훨씬 자연스럽고 읽기 쉬워집니다.

## Trailing Lambdas와 분산락

Trailing Lambdas의 이러한 특성을 분산락 구현에 적용하면, Spring AOP와 비슷하게, 비스니스 로직과 분산락 이라는 횡단 관심사를 분리할 수 있습니다. 특히, 분산락을 적용해야 하는 비즈니스 로직을 람다식으로 정의하고, 이를 고차 함수에 전달함으로써, 분산락 로직과 비즈니스 로직을 명확히 분리할 수 있습니다. 이는 코드의 가독성과 유지보수성을 크게 향상시킵니다.

**Spring AOP를 활용한 분산락**
```kotlin
@DistributedLock("UsePointDomainService.incrementByUserId:#{#userId}")  
override fun incrementByUserId(  
    userId: UUID,  
    amount: Long  
): UserSil {  
    return userPointRepository  
        .getByUserId(userId)  
        .increment(amount)  
        .also { userSilRepository.save(it) }  
}
```

**Trailing Lambdas를 활용한 분산락**

```kotlin
fun incrementByUserId(  
    userId: UUID,  
    amount: Long  
): UserSil = distributedLock("userPointDomainService:$userId") {  
    return@distributedLock userPointRepository  
        .getByUserId(userId)  
        .increment(amount)  
        .also { userSilRepository.save(it) }  
}
```


### 구현과정

우선 분산락 함수를 지원하기 위한 Aspect를 정의합니다. 

```kotlin
@Component  
class DistributedLockAspect(  
    innerRedissonClient: RedissonClient,  
    innerDistributedLockTransactionProcessor: DistributedLockTransactionProcessor,  
) {  
  
    init {  
        redissonClient = innerRedissonClient  
        distributedLockTransactionProcessor = innerDistributedLockTransactionProcessor  
    }  
  
    companion object {  
  
        val logger = KotlinLogging.logger { }  
  
        lateinit var redissonClient: RedissonClient  
            private set  
  
        lateinit var distributedLockTransactionProcessor: DistributedLockTransactionProcessor  
            private set  
  
        const val REDISSON_LOCK_PREFIX = "LOCK:"  
    }  
}
```

Aspect는 스프링 빈으로 정의하여, `RedissonClient`, `DistributedLockTransactionProcessor`를 주입받아 분산락 함수에서 사용할 수 있도록 정적 멤버로 제공합니다.

**RedissonClient**의 경우 분산락획득을 위한 별도 인터페이스를 제공하기때문에 선택하였고, 분산락 모듈의 Config파일에서 빈으로 등록해 주었습니다.

```kotlin
@Configuration  
@ComponentScan(basePackages = ["com.studentcenter.support.lock"])  
class DistributedLockConfig (  
    private val redisProperties: RedisProperties  
){  
  
    @Bean  
    fun redissonClient(): RedissonClient {  
        val redisConfig = Config()  
        redisConfig  
            .useSingleServer()  
            .apply {  
                address = "redis://${redisProperties.host}:${redisProperties.port}"  
            }  
        return Redisson.create(redisConfig)  
    }  
  
}
```

**DistributedLockTransactionProcessor**의 경우 락의 해제가 트랜잭션 커밋이후에 이루어지도록, 부모 트랜잭션의 유무에 관계없이 별도의 트랜잭션을 만들어 동작하게 만들었습니다.

```kotlin
  
@Component  
class DistributedLockTransactionProcessor {  
  
    @Transactional(propagation = Propagation.REQUIRES_NEW)  
    fun <T> proceed(function: () -> T): T {  
        return function()  
    }  
  
}

```

이후 프로젝트 내에서 전역적으로 활용할 수 있도록 패키지레벨의 분산락 함수를 구현합니다.

```kotlin
/**  
 * Distributed Lock * 적용 대상 함수는 별도의 트랜잭션으로 동작하며 커밋 이후 락을 해제한다. * @param key       락 식별자  
 * @param timeUnit  시간 단위  
 * @param waitTime  락을 획득하기 위해 대기할 시간  
 * @param leaseTime 락을 획득한 후 락을 유지할 시간  
 * @param function  적용 대상 함수  
 * @return          함수 실행 결과  
 */fun <T> distributedLock(  
    key: String,  
    timeUnit: TimeUnit = TimeUnit.SECONDS,  
    waitTime: Long = 5L,  
    leaseTime: Long = 3L,  
    function: () -> T,  
): T {  
    val rLock: RLock = (DistributedLockAspect.REDISSON_LOCK_PREFIX + key)  
        .let { DistributedLockAspect.redissonClient.getLock(it) }  
  
    try {  
        val available: Boolean = rLock.tryLock(waitTime, leaseTime, timeUnit)  
        check(available) {  
            throw IllegalStateException("Lock is not available")  
        }  
  
        return DistributedLockAspect.distributedLockTransactionProcessor.proceed(function)  
    } finally {  
        try {  
            rLock.unlock()  
        } catch (e: IllegalMonitorStateException) {  
            DistributedLockAspect.logger.info {  
                "Redisson Lock Already UnLock Key : $key"  
            }  
        }  
    }  
  
}
```


## 테스트

### 분산락 미적용

```kotlin
override fun incrementByUserId(  
    userId: UUID,  
    amount: Long  
): UserSil {  
    return userSilRepository  
        .getByUserId(userId)  
        .increment(amount)  
        .also { userSilRepository.save(it) }  
}
```

```kotlin
@DisplayName("UserSilDomainService 통합 테스트")  
class UserSilDomainServiceIntegrationTest(  
    private val userSilDomainService: UserSilDomainService,  
) : IntegrationTestDescribeSpec({  
  
    describe("유저 실 증가 동시성 테스트") {  
        context("분산락 적용 X") {  
            it("동시성 테스트") {  
                // arrange  
                val userId = UuidCreator.create()  
                userSilDomainService.create(userId)  
  
                val threadCount = 10  
                val incrementAmount = 10L  
  
                // act  
                runBlocking {  
                    repeat(threadCount) {  
                        launch(Dispatchers.Default) {  
                            userSilDomainService.incrementByUserId(userId, incrementAmount)  
                        }                    }  
                }  
  
  
                // assert  
                val userSil: UserSil = userSilDomainService.getByUserId(userId)  
                userSil.amount shouldBe incrementAmount * threadCount  
            }  
        }  
    }  
  
})

```
![[Pasted image 20240224165109.png]]

락이 적용되어있지 않을때에는 동시성 문제가 발생해 포인트가 기댓값에 미치지 못해 테스트가 실패했습니다.


### 분산락 적용

```kotlin
override fun incrementByUserId(  
    userId: UUID,  
    amount: Long  
): UserSil = distributedLock("UseSilDomainService.incrementByUserId:$userId") {  
    return@distributedLock userSilRepository  
        .getByUserId(userId)  
        .increment(amount)  
        .also { userSilRepository.save(it) }  
}
```

![[Pasted image 20240224165303.png]]
분산락을 적용했을때는 기댓값만큼 포인트가 증가해 테스트가 성공하는것을 확인할 수 있었습니다.


## 단위테스트는 어떻게 처리해야하나?

이렇게 trailing lambdas문법을 통해 분산락을 적용하게 되면, DistributedLockAspect에 의존하고 있어 해당 컴포넌트가 스프링 빈으로 등록되지 않는 유닛테스트 환경에서는 에러가 발생하게 됩니다. 이때는 mockk라이브러리를 활용해 DistributedLock에 대한 Static Mocking을 통해 해결할 수 있습니다.

```kotlin
	beforeTest {
        mockkStatic("com.studentcenter.weave.support.lock.DistributedLockKt")
        every {
            distributedLock<Any?>(any(), any(), any(), any(), captureLambda())
        } answers {
            val lambda: () -> Any? = arg<(()-> Any?)>(4)
            lambda()
        }
    }
```


## 마치며

Kotlin의 Trailing Lambdas를 활용한 분산락 구현은 Spring AOP의 한계를 극복하고, 더 안정적이고 유지보수가 쉬운 코드를 작성할 수 있게 만들어 주었습니다. Kotlin의 풍부한 언어 기능을 활용하여, 분산 시스템에서의 동시성 관리를 더 효율적으로 수행할 수 있었습니다.


## Reference
- https://kotlinlang.org/docs/lambdas.html
- https://tech.kakaopay.com/post/overcome-spring-aop-with-kotlin/#%EB%A7%88%EC%B9%98%EB%A9%B0
- https://helloworld.kurly.com/blog/distributed-redisson-lock/