---
tags:
  - Backend
  - Spring
  - JPA
  - Transactional
---


![[PastedImageKakao231215.png]]

최근에 지인에게 테스트코드에서 Transactional 어노테이션을 붙이면 테스트가 성공하고, 어노테이션을 제거하면 테스트가 실패하는데 그 이유를 모르겠다는 질문을 받았다. 

## 문제상황

당시 문제상황을 간단한 샘플코드로 재현해 보았다.

```kotlin
@Entity  
@Table(name = "orders")  
class Order(  
    id: Long? = null,  
) {  
  
    @Id  
    @GeneratedValue(strategy = GenerationType.IDENTITY)  
    var id: Long? = id  
        private set  
  
    var isParcelRegistered: OrderParcelStatus = OrderParcelStatus.PADDING  
        private set  
  
  
    fun registerParcel() {  
        isParcelRegistered = OrderParcelStatus.REGISTERED  
    }  
  
    fun registerParcelFailed() {  
        isParcelRegistered = OrderParcelStatus.REGISTER_FAILED  
    }  
  
  
}

@Service  
class OrderService(  
    private val orderRepository: OrderRepository,  
) {  
  
    @Transactional  
    fun applyParcelEvent(parcelEvent: ParcelEvent) {  
        when (parcelEvent) {  
            is ParcelEvent.Success -> {  
                val order: Order = orderRepository.findById(parcelEvent.orderId).get()  
                order.registerParcel()  
            }  
  
            is ParcelEvent.Failure -> {  
                val order: Order = orderRepository.findById(parcelEvent.orderId).get()  
                order.registerParcelFailed()  
            }  
        }  
    }  
  
}

@SpringBootTest  
class TestCode(  
    @Autowired  
    private val orderService: OrderService,  
    @Autowired  
    private val orderRepository: OrderRepository,  
) {  
  
    @Test  
    @DisplayName("택배 등록 실패 이벤트가 발생하면, 주문의 택배 등록 상태가 실패로 변경된다.")  
    fun checkOrderStatus() {  
        // given  
        val order = Order()  
        val savedOrder: Order = orderRepository.save(order)  
        val failedParcelEvent = ParcelEvent.Failure(savedOrder.id!!)  
  
  
        // when  
        orderService.applyParcelEvent(failedParcelEvent)  
  
        // then  
        Assertions.assertThat(savedOrder.isParcelRegistered).isEqualTo(OrderParcelStatus.REGISTER_FAILED)  
    }  
}

```

처음 `Order`객체를 생성하면 택배 등록 상태를 나타내는 `OrderParcelStatus` 의 값이 `PADDING`인채로 생성된다. 이때 OrderService에 외부에서 발생한 택배 등록 이벤트(`PercelEvent`)를 전달해 주면 이벤트의 성공 실패 종류에 따라 `Order`객체의 `OrderParcelStatus`의 값을 변경한다.

테스트 코드는 택배 등록 이벤트가 전달되면 Order 객체의 값을 변경하는지 검증한다. 우선 택배 등록 실패이벤트가 발생한것을 가정하고 이를 orderService에 전달한다. 그러면 기존에 저장되어있던 Order의 택배 등록 상태가 실패로 변경되기를 기대하고 테스트를 수행한다. 

이때 테스트는 실패한다. 택배 등록 실패이벤트를 전달했음에도 불구하고, 택배의 등록상태가 변경되지 않는다. 왜 그런걸까? 

### savedOrder 객체는 영속성 컨텍스트에서 관리되지 않는다

```kotlin
@SpringBootTest  
class TestCode(  
    @Autowired  
    private val entityManager: EntityManager,  
    @Autowired  
    private val orderService: OrderService,  
    @Autowired  
    private val orderRepository: OrderRepository,  
) {  
  
    @Test  
    @DisplayName("택배 등록 실패 이벤트가 발생하면, 주문의 택배 등록 상태가 실패로 변경된다.")  
    fun checkOrderStatus() {  
        // given  
        val order = Order()  
        val savedOrder: Order = orderRepository.save(order)  
        val failedParcelEvent = ParcelEvent.Failure(savedOrder.id!!)  
  
        println(entityManager.contains(savedOrder)) // false
        
        // when  
        orderService.applyParcelEvent(failedParcelEvent)  
  
        // then  
        Assertions.assertThat(savedOrder.isParcelRegistered).isEqualTo(OrderParcelStatus.REGISTER_FAILED)  
    }  
}
```

테스트 코드에서 위와같이 entityManager를 통해 현재 영속성 컨텍스트에 savedOrder가 있는지 여부를 조회하면 false값이 나온다. 따라서 JPA의 영속성 컨텍스트에서 관리되지 않고있다는 점을 확인할 수 있다. 영속성 컨텍스트에서 관리되지 않으니, 당연히 savedOrder 객체는 더티체킹의 효과를 볼 수 없다.

영속성 컨텍스트는 트랜잭션내에서 관리된다. 때문에 orderRepository로 가져온 savedOrder객체는 트랜잭션 밖, 즉 영속성 컨텍스트 밖이기에 영속성 컨텍스트에 의해 관리되지 않는 객체이다.

때문에 이후 `orderService.applyParcelEvent(...)`를 통해 DB에 저장된 order의 OrderParcelStatus를 변경하더라도, savedOrder의 값은 변화가 없다.

## 영속성 컨텍스트는 트랜잭션 범위 내에서 관리된다

영속성 컨텍스트의 종류는 두가지가 있다.
- Transaction-scoped persistence context
- Extended-scoped persistence context

Transaction-scoped persistence context의 경우 트랜잭션 단위로 영속성 컨텍스트가 유지되는 반면, Extended-scoped persistence context의 경우 컨테이너가 관리하는 영속성 컨텍스트로 여러 트랜잭션에 걸쳐 사용될 수 있다.

> 확장된 퍼시스턴스 컨텍스트를 갖는 EntityManager는 트랜 잭션 스코프의 퍼시스턴스 컨텍스트에서 사용되는 EntityManager 처럼 멀티스레드에서 안전한 프록시 오브젝트가 아니라 멀티스레드에서 안전하지 않은 실제 EntityManager다. 
> 
> - 토비의 스프링 3.1 Vol.2 289p

```
public @interface PersistenceContext {  
  
	...
  
    /**  
     * (Optional) Specifies whether a transaction-scoped persistence context     * or an extended persistence context is to be used.  
     */    PersistenceContextType type() default PersistenceContextType.TRANSACTION;  
	...
}
```
PersistenceContext의 어노테이션을 직접 확인해보면 Transaction-scoped persistence context를 기본값으로 사용한다. Transaction-scoped persistence context를 사용하면 여러 측면에서 다음과 같은 장점이 있다.

**효율성** 
transaction-scoped 영속성 컨텍스트는 트랜잭션이 끝나면 자동으로 종료되므로, 불필요한 리소스 사용을 줄일 수 있다. 반면에 Extended 영속성 컨텍스트는 트랜잭션이 끝나도 종료되지 않고 계속 유지되므로, 리소스 사용이 더 많을 수 있다.

**일관성**
transaction-scoped 영속성 컨텍스트는 트랜잭션 범위 내에서 일관성을 보장한다. 즉, 트랜잭션 내에서 수행된 모든 데이터베이스 작업은 일관된 상태를 유지한다. 반면에 Extended 영속성 컨텍스트는 여러 트랜잭션에 걸쳐 사용될 수 있으므로, 일관성을 보장하기 어려울 수 있다.  

**간단함**
transaction-scoped 영속성 컨텍스트는 트랜잭션을 시작하고 종료하는 것만으로 영속성 컨텍스트를 관리할 수 있다. 반면에 Extended 영속성 컨텍스트는 수동으로 관리해야 하므로, 사용하기 복잡할 수 있다.


실제 트랜잭션을 부여하는 JpaTransactionManager를 살펴보면 EntityManager를 생성해 트랜잭션 단위로 관리하는것을 볼 수 있다.

우선 PlatformTransactionManager의 기본적인 동작 일부를 구현하고 있는 AbstractPlatformTransactionManager를 살펴보면 startTransaction부분에서 TransactionManager의 doBegin메서드를 호출하고있는것을 볼 수 있다.
```java
private TransactionStatus startTransaction(TransactionDefinition definition, Object transaction,  
       boolean nested, boolean debugEnabled, @Nullable SuspendedResourcesHolder suspendedResources) {  
  
    boolean newSynchronization = (getTransactionSynchronization() != SYNCHRONIZATION_NEVER);  
    DefaultTransactionStatus status = newTransactionStatus(  
          definition, transaction, true, newSynchronization, nested, debugEnabled, suspendedResources);  
    this.transactionExecutionListeners.forEach(listener -> listener.beforeBegin(status));  
    try {  
       doBegin(transaction, definition);  
    }  
    catch (RuntimeException | Error ex) {  
       this.transactionExecutionListeners.forEach(listener -> listener.afterBegin(status, ex));  
       throw ex;  
    }  
    prepareSynchronization(status, definition);  
    this.transactionExecutionListeners.forEach(listener -> listener.afterBegin(status, null));  
    return status;  
}```

doBegin메서드는 TransactionManager를 구현하고 있는 JpaTransactionManager에서 확인할 수 있는데, entityManager를 생성하고 있는것을 볼 수 있다.

```java
@Override  
protected void doBegin(Object transaction, TransactionDefinition definition) {  
    JpaTransactionObject txObject = (JpaTransactionObject) transaction;  
  
    if (txObject.hasConnectionHolder() && !txObject.getConnectionHolder().isSynchronizedWithTransaction()) {  
       throw new IllegalTransactionStateException(  
             "Pre-bound JDBC Connection found! JpaTransactionManager does not support " +  
             "running within DataSourceTransactionManager if told to manage the DataSource itself. " +  
             "It is recommended to use a single JpaTransactionManager for all transactions " +  
             "on a single DataSource, no matter whether JPA or JDBC access.");  
    }  
  
    try {  
       if (!txObject.hasEntityManagerHolder() ||  
             txObject.getEntityManagerHolder().isSynchronizedWithTransaction()) {  
          EntityManager newEm = createEntityManagerForTransaction();  
          if (logger.isDebugEnabled()) {  
             logger.debug("Opened new EntityManager [" + newEm + "] for JPA transaction");  
          }  
          txObject.setEntityManagerHolder(new EntityManagerHolder(newEm), true);  
       }  
  
       EntityManager em = txObject.getEntityManagerHolder().getEntityManager();
```

JpaTransactionManager의 doBegin 메서드는 새로운 트랜잭션을 시작할 때 호출된다. 이 메서드에서는 EntityManager를 생성하고 이를 JpaTransactionObject에 저장한다.  
 
JpaTransactionObject는 현재 트랜잭션의 상태를 추적하는 데 사용되며, 트랜잭션 범위 내에서 사용되는 EntityManager를 보유하고 있다. 이렇게 하면 트랜잭션 범위 내에서 동일한 EntityManager 인스턴스가 사용될 수 있다. 이렇게 JpaTransactionManager는 트랜잭션 단위로 EntityManager를 관리한다. 

하지만 항상 영속성 컨텍스트의 생존 범위가 무조건 트랜잭션 범위 내 인것은 아니다. open session in view를 사용하면 영속성 컨텍스트의 범위를 트랜잭션 범위 밖까지 확장할 수 있다.




## Reference
- https://access.redhat.com/documentation/en-us/jboss_enterprise_application_platform_common_criteria_certification/5/html/hibernate_entity_manager_reference_guide/transactions#transactions-basics
- 토비의 스프링 3.1 Vol.2

