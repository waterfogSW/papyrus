우리는 종종 Primitive type이 도메인 객체를 모델링 하기에는 충분한 정보를 제공하지 못하기에 VO(Value Object)를 정의합니다. 

이때 primitive 타입을 wrapping해 VO를 정의하곤 하는데, 추가적인 힙 할당으로 인한 런타임 오버헤드가 발생합니다. Primitive타입은 런타임에 최적화 되어있지만, data class로의 wrapping으로 인해 primitive의 성능 최적화를 의미없게 만듭니다. 이러한 문제를 해결하기 위해, Kotlin은 inline value class고 불리는 특별한 종류의 클래스를 제공합니다.

Kotlin의 value 클래스는 JDK 15부터 도입된 record 클래스의 특성을 가져와서, 불변성(immutability)과 데이터 홀딩(data holding)에 최적화되어 있습니다. value 클래스는 주로 다음과 같이 간결한 구문으로 VO 클래스를 정의하는데 사용됩니다.

```kotlin
@JvmInline 
value class Password(private val s: String)

val securePassword = Password("Don't try this in production")
```

위와 같이 inline value class를 통해 VO를 정의하게 되면 객체 초기화시에 검증 로직을 수행할 수도 있고, 래핑된 primitive type이 런타임에서는 기저에 있는 타입으로 컴파일되어 추가적인 힙 할당으로 인한 런타임 오버헤드가 발생하지 않기도 합니다.


## 하지만 항상 기저 타입으로 컴파일되지는 않습니다

코틀린 공식문서를 확인하면 다음과 같이 기자타입이 사용되지 않는 경우들에 대해 설명하고 있습니다.

```kotlin
interface I  
  
@JvmInline  
value class Foo(val i: Int) : I  
  
fun asInline(f: Foo) {}  
fun <T> asGeneric(x: T) {}  
fun asInterface(i: I) {}  
fun asNullable(i: Foo?) {}  
  
fun <T> id(x: T): T = x  
  
fun main() {  
    val f = Foo(42)  
  
    asInline(f)    // unboxed: used as Foo itself  
    asGeneric(f)   // boxed: used as generic type T  
    asInterface(f) // boxed: used as type I  
    asNullable(f)  // boxed: used as Foo?, which is different from Foo  
  
    // below, 'f' first is boxed (while being passed to 'id') and then unboxed (when returned from 'id')    // In the end, 'c' contains unboxed representation (just '42'), as 'f'    val c = id(f)  
}
```

**1. asInline(f):**
- `asInline` 함수는 인라인 벨류 클래스 타입의 매개변수를 받도록 선언됩니다.
- 컴파일러는 `f`를 직접 사용하여 기저 타입인 `Int` 값에 접근합니다.
- 따라서 boxing/unboxing 없이 값을 효율적으로 처리할 수 있습니다.

**2. asGeneric(f):**
- `asGeneric` 함수는 제네릭 타입 `T`를 매개변수로 받습니다.
- 컴파일러는 `Foo` 인스턴스를 `T` 타입으로 변환해야 하기 때문에 boxing이 발생합니다.

**3. asInterface(i):**
- `asInterface` 함수는 `I` 인터페이스 타입의 매개변수를 받습니다.
- `Foo`는 `I` 인터페이스를 구현하지만, 컴파일러는 여전히 `Foo` 인스턴스를 `I` 타입으로 변환해야 하기 때문에 boxing이 발생합니다.

**4. asNullable(f):**
- `asNullable` 함수는 널 가능한 `Foo` 타입의 매개변수를 받습니다.
- `Foo`는 기본 타입이 아닌 참조 타입이기 때문에 널 가능합니다.
- `asNullable` 함수는 `f`가 null인지 확인하고, null이 아닌 경우 boxing을 수행합니다.
- 결과적으로 `asNullable` 함수는 널 가능한 `Int` 값을 널 가능한 `Integer` 객체로 감싼 형태로 받게 됩니다.

**5. id(f):**
- `id` 함수는 제네릭 타입 `T`를 매개변수로 받고, 그 타입의 값을 그대로 반환하는 항등 함수입니다.
- `f`를 `id` 함수에 전달하면 컴파일러는 `Foo` 인스턴스를 `T` 타입으로 변환해야 하기 때문에 boxing이 발생합니다.
- `id` 함수는 반환 값으로 `T` 타입을 요구하기 때문에, 반환하기 전에 boxing된 값을 unboxing합니다.
- 결과적으로 `id(f)`는 `Int` 값을 반환합니다.


## JPA Entity에서의 Value class 사용
저의 경우, 4번 nullable한 value class를 사용할때 기저타입이 사용되지 않는 현상을 경험했었습니다. 

```kotlin  
@Entity  
@Table(name = "`user`")  
class UserJpaEntity(  
    ...
    height: Height? = null,
	...
) {  
  
	...
  
	@Column(nullable = true, updatable = true, columnDefinition = "integer")  
	var height: Height? = height  
	    private set
  
    ...  
  
}

@JvmInline  
value class Height(val value: Int) {  
  
    init {  
        require(value in 1..300) {  
            "키는 1cm 이상 300cm 이하여야 합니다."  
        }  
    }  
  
}
```

User 엔티티는 Height 라는 속성을 value class로 설정해주었는데, 이때 Height는 nullable하게 다뤄야 하는 속성이었기에 `?`로 nullable한 타입임을 명시해 두었습니다.

이때 IDE에서 다음과 같은 오류를 보여주었습니다.

![[Pasted image 20240205234404.png]]

그대로 실행하게 되면 애플리케이션 실행시 Hibernate에서 다음과 같은 에러를 발생시키며 애플리케이션이 종료되었습니다

```
Caused by: org.hibernate.type.descriptor.java.spi.JdbcTypeRecommendationException: Could not determine recommended JdbcType for Java type 'com.studentcenter.weave.domain.user.vo.Height'
```

이러한 문제의 원인을 찾기위해 바이트 코드로 변환한 후 자바 코드로 디컴파일을 해보았는데, 결과는 다음과 같이 기저 타입이 아닌 Height Type을 사용하고 있었습니다.

```java
@Column(  
   nullable = true,  
   updatable = true,  
   columnDefinition = "integer"  
)  
@Nullable  
private Height height;
```

이후 kotlin코드를 nullable하지 않게 해두고 실행해 보았는데, 기저타입으로 컴파일 된것을 확인할 수 있었습니다.


```java
@Column(  
   nullable = true,  
   updatable = true,  
   columnDefinition = "integer"  
)  
private int height;
```

이러한 이슈로 인해 value class가 항상 기저타입으로 컴파일되어 런타임에 사용되지는 않음을 확인할 수 있었습니다.

## 결론

value class의 사용은 도메인 객체 모델링에 도움을 주고 애플리케이션 로직을 직관적으로 파악할 수 있게 도움을 준다는 장점이 있지만, 외부 인프라 레이어에서 사용하기에는 애매한 부분이 있다고 생각이 들었습니다.

deserializing시 리플렉션을 통해 초기화 되어 value class의 init 블럭에 있는 validation이 수행되지 않는다던가, nullable한 value class사용으로 인해 jpa entity에서 원치않는 타입이 사용되는 문제등 외부 인프라 연동시 발생하는 문제점들이 존재했습니다.

이러한 문제를 해결하기 위해 application layer에서는 value class를 적극적으로 사용하되, 외부 인프라 레이어에서는 unboxing해 기저 타입을 사용하도록 컨벤션을 두었습니다.