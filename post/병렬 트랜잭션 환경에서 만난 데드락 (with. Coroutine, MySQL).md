
한 테이블에 여러개의 데이터를 한번에 생성해야하는 API를 설계하면서, 성능을 개선하기 위해 각 데이터를 별개의 트랜잭션으로 나누어 DB에 병렬적으로 삽입을 요청하는 과정에서 데드락 이슈를 만나게 되었습니다.
이를 간단한 예시코드와 함께 해결해 나가는 과정을 다루어 보겠습니다.

## 초기 구현
---
**요구 사항**
- 제품 배치 생성 API
- 배치 내의 각 제품 생성 요청은 별개의 트랜잭션으로 처리되어야 한다.
- **제품명의 중복은 허용되지 않는다.**

**테이블 설계**
```sql
CREATE TABLE product  
(  
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,  
    name        VARCHAR(255) NOT NULL,  
    description TEXT         NOT NULL  
);  
CREATE UNIQUE INDEX Product_name_uindex ON product (name);
```


위와 같은 요구사항을 해결하기 위해 다음과 같이 구현을 진행했습니다.

**배치 생성 UseCase**
```kotlin
interface ProductBatchCreateUseCase {  
  
    fun invoke(commands: List<Command>): List<Result>  
  
    data class Command(  
        val name: String,  
        val description: String,  
    )  
  
    sealed class Result {  
        data class Success(val postId: PostId) : Result()  
        data class Failure(  
            val name: String,  
            val message: String  
        ) : Result()  
    }  
}

@Service  
class ProductBatchCreate(  
    private val productCreateUseCase: ProductCreateUseCase  
) : ProductBatchCreateUseCase {  
  
    override fun invoke(commands: List<ProductBatchCreateUseCase.Command>): List<ProductBatchCreateUseCase.Result> {  
        val results: List<ProductCreateUseCase.Result> = commands.map {  
            productCreateUseCase.invoke(  
                command = ProductCreateUseCase.Command(  
                    name = it.name,  
                    content = it.description  
                )  
            )  
        }  
  
        return results.map {  
            when (it) {  
                is ProductCreateUseCase.Result.Success -> mapToSuccess(it)  
                is ProductCreateUseCase.Result.Failure -> mapToFailure(it)  
            }  
        }  
    }  
  
    private fun mapToSuccess(result: ProductCreateUseCase.Result.Success): ProductBatchCreateUseCase.Result.Success {  
        return ProductBatchCreateUseCase.Result.Success(postId = result.id)  
    }  
  
    private fun mapToFailure(result: ProductCreateUseCase.Result.Failure): ProductBatchCreateUseCase.Result.Failure {  
        return ProductBatchCreateUseCase.Result.Failure(  
            name = result.title,  
            message = result.message,  
        )  
    }  
}
```

**단건 생성 UseCase**
```kotlin
interface ProductCreateUseCase {  
  
    fun invoke(command: Command): Result  
  
    data class Command(  
        val name: String,  
        val content: String,  
    )  
  
    sealed class Result {  
        data class Success(val id: PostId) : Result()  
        data class Failure(  
            val title: String,  
            val message: String  
        ) : Result()  
    }  
}

@Service  
class ProductCreate(  
    private val productRepository: ProductRepository  
) : ProductCreateUseCase {  
  
    @Transactional(propagation = Propagation.REQUIRES_NEW)  
    override fun invoke(command: ProductCreateUseCase.Command): ProductCreateUseCase.Result {  
        val product: Product = Product.create(  
            name = command.name,  
            content = command.content,  
        )  
  
        if (isDuplicateTitle(product.name)) {  
            return ProductCreateUseCase.Result.Failure(  
                title = product.name,  
                message = "중복된 상품 명입니다."  
            )  
        }  
  
        val savedProduct: Product = productRepository.save(product)  
  
        return ProductCreateUseCase.Result.Success(id = savedProduct.id)  
    }  
  
    private fun isDuplicateTitle(title: String): Boolean {  
        return productRepository.existsByName(title)  
    }  
}
```

단건 생성의 경우 별개의 트랜잭션으로 처리됨을 보장하고 명시하기 위해 Propagation을 `REQUIRES_NEW`로 두었습니다.

중복여부는 Duplicate Key 에러로도 확인할 수 있지만, `DataIntegrityViolationException` 안에 포함된 메시지를 파싱해 중복으로 인한 에러인지 혹은 다른 에러인지 판단해야하고 DB에 의존적이라는 문제가 있습니다. 때문에 제품의 중복 여부를 애플리케이션 레벨에서도 확인할 수 있어야 한다는 판단에 중복확인을 위한 validation 로직을 작성하게 되었습니다.

배치 생성이 정상적으로 이루어 지는지 통합 테스트를 통해 확인해 보았습니다.

```kotlin
@SpringBootTest  
@ContextConfiguration(classes = [IntegrationTestSetup::class])  
class ProductBatchCreateTest(  
    private val sut: ProductBatchCreateUseCase  
) : FunSpec({  
  
    test("제품 배치 생성") {  
        // given  
        val commands: List<ProductBatchCreateUseCase.Command> = (0 until 10).map {  
            ProductBatchCreateUseCase.Command(  
                name = "제품",  
                description = "제품 $it 설명"  
            )  
        }  
  
        // when  
        val results: List<ProductBatchCreateUseCase.Result> = sut.invoke(commands)  
  
        // then  
        results.filterIsInstance<ProductBatchCreateUseCase.Result.Success>().size shouldBe 10  
    }

	test("제품 배치 생성 시간 측정") {  
    // given  
    val commands: List<ProductBatchCreateUseCase.Command> = (0 until 1000).map {  
        ProductBatchCreateUseCase.Command(  
            name = "제품 $it",  
            description = "제품 $it 설명"  
        )  
    }  
  
    // when, then  
    measureTimeMillis { sut.invoke(commands) }  
        .also { time -> println("제품 배치 생성 시간: $time ms") }
	}
})
```

```
제품 배치 생성 시간: 6998 ms
```

통합테스트의 경우 TestContainer를 통해 운영 코드와 동일한 환경에서 테스트 했습니다. 제품 배치 생성의 경우 한개 생성 요청을 처리하면 그다음 생성 요청을 순차적으로 처리하는 방식으로 구현되어 있는데, 이러한 방식의 구현은 효율적이지 않습니다.

## 코루틴 병렬처리 적용
---

각 생성 요청은 하나의 트랜잭션으로 묶여있을 필요가 없기 때문에 병렬적으로 처리 가능합니다. 이를 위해 배치 생성 요청을 코루틴을 활용한 병렬 처리 방식으로 개선하고 생성 시간을 측정해 보았습니다.


```kotlin
	override suspend fun invoke(commands: List<ProductBatchCreateUseCase.Command>): List<ProductBatchCreateUseCase.Result> =  
    coroutineScope {  
        val deferredResults: List<Deferred<ProductCreateUseCase.Result>> = commands.map { command ->  
            async(Dispatchers.IO) {  
                productCreateUseCase.invoke(  
                    ProductCreateUseCase.Command(  
                        name = command.name,  
                        content = command.description  
                    )  
                )  
            }  
        }  
  
        deferredResults.awaitAll().map { result ->  
            when (result) {  
                is ProductCreateUseCase.Result.Success -> mapToSuccess(result)  
                is ProductCreateUseCase.Result.Failure -> mapToFailure(result)  
            }  
        }  
    }
```

```
제품 배치 생성 시간: 1593 ms
```

1000개의 데이터를 생성하는 테스트로 확인해본 결과 수행시간이 6998ms에서 1593ms으로 개선되었습니다. 오차를 감안하더라도 크게 개선된 수치입니다.

성능은 개선되었지만, 새로운 문제점이 발생했습니다. 만약 배치 생성 요청 내에서 중복된 제품명이 존재하는 경우  ProductCreate의 `isDuplicateTitle` 메서드가 제품명의 중복을 정상적으로 확인하지 못하고, `productRepository.save(product)`를 호출하게 됨으로써, DB의 `DataIntegrityViolationException`을 발생시키게 된다는 점입니다.

```
A 트랜잭션
select * from product where name = "중복이름"
insert into product (name, description) values ('중복이름', 'test');


B 트랜잭션
select * from product where name = "중복이름"
insert into product (name, description) values ('중복이름', 'test');
```
현재 MySQL의 트랜잭션 격리 수준은 기본값인 REPETABLE_READ격리 수준인데, 병렬적으로 수행되는 두 트랜잭션이 트랜잭션 수행전 스냅샷을 기준으로 select 쿼리를 수행하기 때문에 여러 트랜잭션이 중복된 name을 가지고 있더라도 select시에는 조회가 되지 않기 때문에 insert query는 수행되게 됩니다.

이러한 문제를 해결하기 위해 isDuplicateTitle메서드의 쿼리를 select .. for update를 사용해 쓰기잠금을 걸어 개선해 보려 시도해 보았습니다.

## 데드락
---

```
could not execute statement [Deadlock found when trying to get lock; try restarting transaction] [insert into product (description,name) values (?,?)]; SQL [insert into product (description,name) values (?,?)]

org.springframework.dao.CannotAcquireLockException: could not execute statement [Deadlock found when trying to get lock; try restarting transaction] [insert into product (description,name) values (?,?)]; SQL [insert into product (description,name) values (?,?)]
```

테스트를 수행해본 결과 위와 같은 오류의 데드락을 확인할 수 있었습니다. MySQL 콘솔에서는 `SHOW ENGINE INNODB STATUS` 명령어를 통해 최근에 발생한 데드락에 대한 정보를 확인할 수 있었습니다.

```
**트랜잭션 (1)**

(1) HOLDS THE LOCK(S):

RECORD LOCKS space id 2 page no 5 n bits 80 index Product_name_uindex of table `test`.`product` trx id 2984 lock_mode X locks gap before rec
Record lock, heap no 8 PHYSICAL RECORD: n_fields 2; compact format; info bits 0

(1) WAITING FOR THIS LOCK TO BE GRANTED:

RECORD LOCKS space id 2 page no 5 n bits 80 index Product_name_uindex of table `test`.`product` trx id 2984 lock_mode X locks gap before rec insert intention waiting_
Record lock, heap no 8 PHYSICAL RECORD: n_fields 2; compact format; info bits 0

**트랜잭션 (2)**

(2) HOLDS THE LOCK(S):

RECORD LOCKS space id 2 page no 5 n bits 80 index Product_name_uindex of table `test`.`product` trx id 2992 lock_mode X locks gap before rec
Record lock, heap no 8 PHYSICAL RECORD: n_fields 2; compact format; info bits 0_

(2) WAITING FOR THIS LOCK TO BE GRANTED:
RECORD LOCKS space id 2 page no 5 n bits 80 index Product_name_uindex of table `test`.`product` trx id 2992 lock_mode X locks gap before rec insert intention waiting

Record lock, heap no 8 PHYSICAL RECORD: n_fields 2; compact format; info bits 0_
```


발생한 로그를 분석해 보면 다음과 같습니다.

**트랜잭션 1**
- **상태**: 삽입 중, 락 대기 중
- **행위**: `product` 테이블에 `insert` 쿼리 실행
- **락 정보**:
    - **보유 중인 락**: Product_name_uindex에 대한 X 락 및 갭 락(gap lock)
    - **대기 중인 락**: 동일한 인덱스에 대한 X 갭 락 및 삽입 의도 락(insert intention lock)

**트랜잭션 2**
- **상태**: 삽입 중, 락 대기 중
- **행위**: `product` 테이블에 `insert` 쿼리 실행
- **락 정보**:
    - **보유 중인 락**: Product_name_uindex에 대한 X 락 및 갭 락(gap lock)
    - **대기 중인 락**: 동일한 인덱스에 대한 X 갭 락 및 삽입 의도 락(insert intention lock)

여기서 한가지 의문이 들 수 도 있는데, 두 트랜잭션이 보유중인 락이 베타적 락(lock_mode : X)이라는 점입니다. 일반적으로 베타적 락은 동시에 소유할수 없다고 알고 있는데, 로그엔 두 트랜잭션이 동일한 위치에 베타적 락을 소유하고 있는것으로 보입니다. 여기에 대한 답은 MySQL의 공식문서에서 확인해 볼 수 있습니다.

> [MySQL 공식문서 - Gap lock](https://dev.mysql.com/doc/refman/8.0/en/innodb-locking.html#innodb-gap-locks)
> Gap locks in `InnoDB` are “purely inhibitive”, which means that their only purpose is to prevent other transactions from inserting to the gap. Gap locks can co-exist. A gap lock taken by one transaction does not prevent another transaction from taking a gap lock on the same gap. There is no difference between shared and exclusive gap locks. They do not conflict with each other, and they perform the same function.

갭 락(gap lock)의 경우 여러 트랜잭션이 동일한 갭에 대해 갭락을 가질 수 있으며, 충돌하지 않는다고 설명하고 있습니다. 이러한 의문점이 해소가 된다면 위의 로그를 통해 데드락의 발생원인을 명확히 파악할 수 있습니다. 

실제로 존재하지 않는 데이터에 대해 select * for update를 쿼리를 날려 갭락이 발생했으며, 갭락은 여러 트랜잭션에서 공존할 수 있기 때문에 두 트랜잭션이 동시에 획득한 상태가 됩니다.

이때 각 트랜잭션은 이후 삽입쿼리를 위해 삽입 의도 락(insert intention lock)을 획득하려 하는데 이는 갭락과 호환되지 않기 때문에 두 트랜잭션이 서로의 갭 락을 기다리게 되고, 트랜잭션이 끝나지 않으므로 gap lock을 획득하지 못한 상태가 유지되며 데드락이 발생하게 된 것 입니다.

Gap lock으로 인한 데드락을 없애기 위해서는 Repeatable Read격리수준을 사용해 Gap락을 명시적으로 사용하지 않도록 하면 됩니다.

Repeatable Read 격리수준에서는 트랜잭션이 시작될 때 읽은 데이터가 트랜잭션이 종료될 때까지 변경되지 않음을 보장합니다. 이를 위해서는 다른 트랜잭션이 특정 간격에 데이터를 삽입 하지 않음이 보장되어야 하는데, MySQL에서는 이를 갭락으로 해결합니다.

때문에 Read Committed 격리수준을 사용하면 갭 락의 사용을 명시적으로 해제할 수 있습니다. 다만 binary log format을 row로 설정하는 등의 격리수준 하향에 따른 부수효과에 대한 대응도 염두에 두어야 합니다.

## Synchronized 키워드 사용
---
select ... for update는Read Committed 레벨에서 어떠한 잠금도 발생시키지 않기때문에, 여전히 중복된 값을 삽입하여 `DataIntegrityViolationException`을 발생시키게 됩니다. 또한 Repeatable Read 레벨에서는 앞서 보았던 바와 같이 Deadlock을 발생시켰습니다.

이후 생각한 방법은 애플리케이션 레벨에서 완전히 로직을 제어하기 위해 validation로직을 구현한 것이니 분산락을 활용하거나, synchronized 키워드를 사용하는것이었는데, 보다 공수가 덜드는 방식인 synchronized 키워드를 통해 애플리케이션 레벨의 락을 잡는것이 좋겠다는 판단했습니다.

최종적으로 코드는 다음과 같이 개선되었습니다. 물론 syncrhonized키워드를 사용하게 되면서 코루틴사용으로 인한 병렬처리의 이점은 제한적일수는 있지만, 

### 기존코드
```kotlin

@Service  
class ProductBatchCreate(  
    private val productCreateUseCase: ProductCreateUseCase  
) : ProductBatchCreateUseCase {  
  
  
    override fun invoke(commands: List<ProductBatchCreateUseCase.Command>): List<ProductBatchCreateUseCase.Result> {  
        val results: List<ProductCreateUseCase.Result> = commands.map {  
            productCreateUseCase.invoke(  
                command = ProductCreateUseCase.Command(  
                    name = it.name,  
                    description = it.description  
                )  
            )  
        }  
  
        return results.map {  
            when (it) {  
                is ProductCreateUseCase.Result.Success -> mapToSuccess(it)  
                is ProductCreateUseCase.Result.Failure -> mapToFailure(it)  
            }  
        }  
    }  
	...
}

@Service  
class ProductCreate(  
    private val productRepository: ProductRepository  
) : ProductCreateUseCase {  
      
    @Transactional(propagation = Propagation.REQUIRES_NEW)  
    override fun invoke(command: ProductCreateUseCase.Command): ProductCreateUseCase.Result {  
        val product: Product = Product.create(  
            name = command.name,  
            description = command.description,  
        )  
  
        if (isDuplicateTitle(product.name)) {  
            return ProductCreateUseCase.Result.Failure(  
                title = product.name,  
                message = "중복된 상품 명입니다."  
            )  
        }  
  
        val savedProduct: Product = productRepository.save(product)  
  
        return ProductCreateUseCase.Result.Success(id = savedProduct.id)  
    }  
  
    private fun isDuplicateTitle(title: String): Boolean {  
        return productRepository.findByName(title) != null  
    }  
}

```

**데이터 10,000개 생성 시간: 39503 ms**


### 개선된 코드

```kotlin

@Service  
class ProductBatchCreate(  
    private val productCreateUseCase: ProductCreateUseCase  
) : ProductBatchCreateUseCase {  
  
    override suspend fun invoke(commands: List<ProductBatchCreateUseCase.Command>): List<ProductBatchCreateUseCase.Result> =  
        coroutineScope {  
            val deferredResults: List<Deferred<ProductCreateUseCase.Result>> = commands.map { command ->  
                async(Dispatchers.IO) {  
                    productCreateUseCase.invoke(  
                        ProductCreateUseCase.Command(  
                            name = command.name,  
                            description = command.description  
                        )  
                    )  
                }  
            }  
  
            deferredResults.awaitAll().map { result ->  
                when (result) {  
                    is ProductCreateUseCase.Result.Success -> mapToSuccess(result)  
                    is ProductCreateUseCase.Result.Failure -> mapToFailure(result)  
                }  
            }  
        }  
	...
}


@Service  
class ProductCreate(  
    private val productRepository: ProductRepository  
) : ProductCreateUseCase {  
  
    @Synchronized  
    @Transactional(propagation = Propagation.REQUIRES_NEW)  
    override fun invoke(command: ProductCreateUseCase.Command): ProductCreateUseCase.Result {  
        val product: Product = Product.create(  
            name = command.name,  
            description = command.description,  
        )  
  
        if (isDuplicateTitle(product.name)) {  
            return ProductCreateUseCase.Result.Failure(  
                title = product.name,  
                message = "중복된 상품 명입니다."  
            )  
        }  
  
        val savedProduct: Product = productRepository.save(product)  
  
        return ProductCreateUseCase.Result.Success(id = savedProduct.id)  
    }  
  
    private fun isDuplicateTitle(title: String): Boolean {  
        return productRepository.findByName(title) != null  
    }  
}
```

**데이터 10,000개 생성 시간: 16004 ms**