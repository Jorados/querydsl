package study.querydsl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.dto.MemberDto;
import study.querydsl.dto.QMemberDto;
import study.querydsl.dto.UserDto;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.QTeam;
import study.querydsl.entity.Team;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceUnit;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static study.querydsl.entity.QMember.*;
import static study.querydsl.entity.QTeam.*;

@SpringBootTest
@Transactional
public class QuerydslBasicTest {

    @Autowired
    EntityManager em;

    @Autowired
    JPAQueryFactory queryFactory;

    @BeforeEach
    public void before() {
        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");
        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);
        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member4", 40, teamB);

        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);
    }

    @Test
    public void startJPQL() {
        //member1을 찾아라.
        Member findMember = em.createQuery("select m from Member m where m.username =:username", Member.class)
                .setParameter("username", "member1")
                .getSingleResult();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void startQuerydsl() {
        //1.JPAQueryFactory를 만들때 생성자에 em을 같이 넘겨줘야한다.
        //2.QMember는 변수명에 별칭을 준다. //별로 안중요
        //3.파라미터 바인딩 자동으로 잡아준다.
        //4.jpql은 문자로 작성되지만, querydsl은 컴파일 시점에 문법오류를 잡아주고 코드어시스턴스도 사용가능

        //QMember m = new QMember("m");
        //QMember m = QMember.member;
        //static import 처리 해서 사용가능 //왠만하면 이 방식으로.
        JPAQueryFactory queryFactory = new JPAQueryFactory(em);
        Member findMember = queryFactory
                .select(member)
                .from(member)
                .where(member.username.eq("member1")) //파라미터 바인딩 처리
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    //검색조건쿼리
    @Test
    public void search() {
        JPAQueryFactory queryFactory = new JPAQueryFactory(em);
        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1")
                        .and(member.age.eq(10)))
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void searchAndParam() {
        JPAQueryFactory queryFactory = new JPAQueryFactory(em);
        Member findMember = queryFactory
                .selectFrom(member)
                .where(
                        member.username.eq("member1"),
                        member.age.eq(10)
                )
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    //결과조회
    @Test
    public void resultFetch() {
        JPAQueryFactory queryFactory = new JPAQueryFactory(em);
        //쿼리의 목록을 리스트로 조회
        List<Member> fetch = queryFactory
                .selectFrom(member)
                .fetch();

        //단건 조회
        Member fetchOne = queryFactory
                .selectFrom(member)
                .fetchOne();

        //리미트걸고 패치원
        Member fetchFirst = queryFactory
                .selectFrom(member)
                .fetchFirst();
        //.limit(1).fetchOne()

        //페이징 --> decprecated -> count 쿼리가 필요하다면 별도로 작성해야 한다
        QueryResults<Member> results = queryFactory
                .selectFrom(member)
                .fetchResults();

        results.getTotal();
        List<Member> content = results.getResults();

        //페이징 토탈카운트 구할때는 그냥 이렇게
        //다른거 할때는 그냥 fetch() 사용
        Long totalCount = queryFactory
                .select(member.count())
                .from(member)
                .fetchOne();
        assertThat(totalCount.longValue()).isEqualTo(4);


        //count 쿼리로 변경 -> decprecated -> count 쿼리가 필요하다면 별도로 작성해야 한다
        long count = queryFactory
                .selectFrom(member)
                .fetchCount();
    }

    /**
     * 회원 정렬 순서
     * 1.회원 나이 내림차순(sec)
     * 2.회원 이름 올림차순(asc)
     * 단 2에서 회원 이름이 없으면 마지막에 출력(nulls last)
     */
    @Test
    public void sort() {
        JPAQueryFactory queryFactory = new JPAQueryFactory(em);

        em.persist(new Member(null, 100));
        em.persist(new Member("member5", 100));
        em.persist(new Member("member6", 100));

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(100))
                .orderBy(member.age.desc(), member.username.asc().nullsLast())
                .fetch();

        Member member5 = result.get(0);
        Member member6 = result.get(1);
        Member memberNull = result.get(2);
        assertThat(member5.getUsername()).isEqualTo("member5");
        assertThat(member6.getUsername()).isEqualTo("member6");
        assertThat(memberNull.getUsername()).isNull();

    }

    //페이징
    @Test
    public void paging1() {
        JPAQueryFactory queryFactory = new JPAQueryFactory(em);
        List<Member> result = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1)
                .limit(2)
                .fetch();

        assertThat(result.size()).isEqualTo(2);

    }

    @Test
    public void paging2() {
        JPAQueryFactory queryFactory = new JPAQueryFactory(em);

        QueryResults<Member> queryResults = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1)
                .limit(2)
                .fetchResults();

        Long totalCount = queryFactory
                .select(member.count())
                .from(member)
                .fetchOne();

        assertThat(totalCount.longValue()).isEqualTo(4);

        assertThat(queryResults.getTotal()).isEqualTo(4);
        assertThat(queryResults.getLimit()).isEqualTo(2);
        assertThat(queryResults.getOffset()).isEqualTo(1);
        assertThat(queryResults.getResults()).isEqualTo(2);
    }

    //집합
    @Test
    public void aggregation() throws Exception {
        JPAQueryFactory queryFactory = new JPAQueryFactory(em);

        List<Tuple> result = queryFactory
                .select(member.count(),
                        member.age.sum(),
                        member.age.avg(),
                        member.age.max(),
                        member.age.min())
                .from(member)
                .fetch();

        Tuple tuple = result.get(0); //데이터 값은 1개라서
        assertThat(tuple.get(member.count())).isEqualTo(4);
        assertThat(tuple.get(member.age.sum())).isEqualTo(100);
        assertThat(tuple.get(member.age.avg())).isEqualTo(25);
        assertThat(tuple.get(member.age.max())).isEqualTo(40);
        assertThat(tuple.get(member.age.min())).isEqualTo(10);
    }

    //group by
    //팀의 이름과 각 팀의 평균 연령을 구해라.
    @Test
    public void group() {
        List<Tuple> result = queryFactory
                .select(team.name, member.age.avg())
                .from(member)
                .join(member.team, team) //member의 team과 team 조인
                .groupBy(team.name) //team의 이름으로 그룹핑
                .fetch();

        Tuple teamA = result.get(0);
        Tuple teamB = result.get(1);

        assertThat(teamA.get(team.name)).isEqualTo("teamA");
        assertThat(teamA.get(member.age.avg())).isEqualTo(15);

        assertThat(teamB.get(team.name)).isEqualTo("teamB");
        assertThat(teamB.get(member.age.avg())).isEqualTo(35);
    }

    /**
     * 팀 A에 소속된 모든 회원
     * 조인
     */
    @Test
    public void join() {
        List<Member> result = queryFactory
                .selectFrom(member)
                .leftJoin(member.team, team)
                .where(team.name.eq("teamA"))
                .fetch();

        assertThat(result)
                .extracting("username")
                .containsExactly("member1", "member2");
    }

    /**
     * 세타조인
     * 회원의 이름이 팀 이름과 같은 회원 조회
     * 연관관계가 없는 필드 조인
     */

    @Test
    public void theta_join() {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));

        List<Member> result = queryFactory
                .select(member)
                .from(member, team)
                .where(member.username.eq(team.name))
                .fetch();

        assertThat(result)
                .extracting("username")
                .containsExactly("teamA", "teamB");
    }

    /** 조인 on절
     *  1.조인 대상 필터링
     *  2.연관관계 없는 엔티티 외부 조인
     */

    /**
     * 예) 회원과 팀을 조인하면서, 팀 이름이 teamA인 팀만 조인, 회원은 모두 조회
     * JPQL:select m,t from Member m left join m.team t on t.name = 'teamA'
     */
    @Test
    public void join_on_filtering() {
        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(member.team, team).on(team.name.eq("teamA"))
                .fetch();

        //일반 join으로 하게되면 null은 다 걸러져서 출력되기때문에
        //where문으로 하는것이랑 같다
        //.join(member.team,team)
        //.where(team.name.eq("teamA")

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /**
     * 연관관계가 없는 엔티티 외부 조인
     * 회원의 이름이 팀 이름과 같은 대상 외부 조인
     */
    @Test
    public void join_on_no_relation() {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));

        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(team).on(member.username.eq(team.name))
                .fetch();
        //on 조인
        //원래는 .leftjoin(member.team, team) 이렇게 조인하는데 id값끼리 매칭됨
        //id 매칭이 아니고 이름으로만(member.username)=(team.name) 조인대상이 필터링된다.

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /**
     * 패치 조인
     */
    @PersistenceUnit
    EntityManagerFactory emf;

    @Test
    public void fetchJoinNo() {
        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1"))
                .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("페치 조인 미적용").isFalse();
    }

    @Test
    public void fetchJoinUse() {
        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(member)
                .join(member.team, team).fetchJoin()
                .where(member.username.eq("member1"))
                .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("페치 조인 적용").isTrue();
    }

    /**
     * 서브쿼리
     * 나이가 가장 많은 회원 조회
     */
    @Test
    public void subQuery() {
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(
                        JPAExpressions
                                .select(memberSub.age.max())
                                .from(memberSub) // ->  40을 의미함
                ))
                .fetch();

        assertThat(result).extracting("age")
                .containsExactly(40);
    }

    /**
     * 서브쿼리
     * 나이가 평균이상
     */
    @Test
    public void subQueryGoe() {
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.goe(
                        JPAExpressions
                                .select(memberSub.age.avg())
                                .from(memberSub) // ->  30,40
                ))
                .fetch();

        assertThat(result).extracting("age")
                .containsExactly(30, 40);
    }

    /**
     * 서브쿼리 여러 건 처리, in 사용
     * 10살보다 많은 사람
     */
    @Test
    public void subQueryIn() {
        QMember memberSub = new QMember("memberSub");
        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.in(
                        JPAExpressions
                                .select(memberSub.age)
                                .from(memberSub)
                                .where(memberSub.age.gt(10))
                ))
                .fetch();
        assertThat(result).extracting("age")
                .containsExactly(20, 30, 40);
    }

    @Test
    public void selectSubQuery() {
        QMember memberSub = new QMember("memberSub");
        List<Tuple> result = queryFactory
                .select(member.username,
                        JPAExpressions
                                .select(memberSub.age.avg())
                                .from(memberSub))
                .from(member)
                .fetch();
        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    //!!!!!!!!!!!!!!!!!!
    //jpa서브쿼리의 한계-> from절의 서브쿼리가 안된다. querydsl도 안된다.
    //해결방안
    //1.서브쿼리를 join으로 변경한다.
    //2.애플리케이션에서 쿼리를 2번 분리해서 실행한다.
    //3.nativeSQL을 사용한다.

    /**
     * case문
     */
    @Test
    public void basicCase() {
        List<String> result = queryFactory
                .select(member.age
                        .when(10).then("열살")
                        .when(20).then("스무살")
                        .otherwise("기타"))
                .from(member)
                .fetch();
        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    public void complexCase() {
        List<String> result = queryFactory
                .select(new CaseBuilder()
                        .when(member.age.between(0, 20)).then("0~20살")
                        .when(member.age.between(21, 30)).then("21~30살")
                        .otherwise("기타"))
                .from(member)
                .fetch();
        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    /**
     * 상수 , 문자 더하기
     */

    @Test
    public void constant() {
        List<Tuple> result = queryFactory
                .select(member.username, Expressions.constant("A"))
                .from(member)
                .fetch();
        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    @Test
    public void concat() {
        //{username}_{age}
        List<String> result = queryFactory
                .select(member.username.concat("_").concat(member.age.stringValue()))
                .from(member)
                .where(member.username.eq("member1"))
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    //////////////////////////////중급 문법/////////////////////////////////
    //프로젝션과 결과 반환

    //프로젝션 대상이 하나인 경우
    @Test
    public void simpleProjection() {
        List<String> result = queryFactory
                .select(member.username)
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    //프로젝션 대상이 둘 이상인 경우
    @Test
    public void tupleProjection() {
        List<Tuple> result = queryFactory
                .select(member.username, member.age)
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            String username = tuple.get(member.username);
            Integer age = tuple.get(member.age);
            System.out.println("username = " + username);
            System.out.println("age = " + age);
        }
    }

    //프로젝션과 결과 반환 - DTO 조회 / jpql 방식
    @Test
    public void findDtoByJPQL(){
        List<MemberDto> result = em.createQuery("select new study.querydsl.dto.MemberDto(m.username,m.age) from Member m", MemberDto.class)
                .getResultList();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }

    }

    //DTO 조회 / Querydsl 방식
    // 1.프로퍼티 접근 -setter 주입 2.필드 직접 접근 3.생성자 사용
   //1.프로퍼티 접근
    @Test
    public void findDtoBySetter(){
        List<MemberDto> result = queryFactory
                .select(Projections.bean(MemberDto.class
                        ,member.username
                        ,member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    //2. 필드 접근 -> getter,setter 없이 가능
    @Test
    public void findDtoByField(){
        List<MemberDto> result = queryFactory
                .select(Projections.fields(MemberDto.class
                        ,member.username
                        ,member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    //필드로 접근 하기 때문에 필드 명이 맞아야 함.
    //.as("name")해주면 됨
    @Test
    public void findUserDtoByField(){
        QMember memberSub = new QMember("memberSub");
        List<UserDto> result = queryFactory
                .select(Projections.fields(UserDto.class
                        ,member.username.as("name")
                        ,ExpressionUtils.as(JPAExpressions
                                .select(memberSub.age.max())
                                    .from(memberSub),"age")
                ))
                .from(member)
                .fetch();

        for (UserDto userDto : result) {
            System.out.println("userDto = " + userDto);
        }
    }

    //3.생성자 사용
    //Dto 객체와 타입이 맞아야 됨.
    @Test
    public void findDtoByConstructor(){
        List<MemberDto> result = queryFactory
                .select(Projections.constructor(MemberDto.class
                        ,member.username
                        ,member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    //@QueryProjection -> gradle -> compileQuerydsl
    //constructor를 이용했을 때는 에러가 발생하면 런타임 즉, 에러가 발생했을때 오류는 잡는다
    //QMemberDto를 이용해서 하게되면 만들어진것 이외에 , 파라미터 타입이 잘못된값이 들어가면 빨간줄이 뜬다.
    //querydsl 어노테이션을 유지해야 하는 점과 DTO까지 Q파일을 생성해야하는 단점이 있다.
    @Test
    public void findDtoByQueryProjection(){
        List<MemberDto> result = queryFactory
                .select(new QMemberDto(member.username, member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    //동적쿼리를 해결하는 방법
    //1.BooleanBuilder
    @Test
    public void dynamicQuery_BooleanBuilder(){
        String usernameParam = "member1";
        Integer ageParam = 10;

        List<Member> result = searchMember1(usernameParam,ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    //사용자 이름이 있을 땐 이름으로, 나이가 있을 땐 나이로 검색하는 동적쿼리가 가능하다.
    private List<Member> searchMember1(String usernameParam, Integer ageParam) {
        //usernameParam값이 null인 경우,ageParam값이 null인 경우
        BooleanBuilder builder = new BooleanBuilder();
        if(usernameParam != null){
            builder.and(member.username.eq(usernameParam));
        }
        if(ageParam != null){
            builder.and(member.age.eq(ageParam));
        }
        return queryFactory
                .selectFrom(member)
                .where(builder)
                .fetch();
    }

    //동적쿼리를 해결하는 방법
    //2.Where 다중 파라미터 사용
    //where 조건에 'null' 값은 무시된다
    //메서드를 다른 쿼리에서도 재활용 할 수 있다.
    //쿼리 자체의 가독성이 높아진다.
    @Test
    public void dynammicQuery_WhereParam(){
        String usernameParam = "member1";
        Integer ageParam = 10;

        List<Member> result = searchMember2(usernameParam,ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    //where절의 (응답)값이 null이면 and가 아니고 무시한다.
    private List<Member> searchMember2(String usernameCond, Integer ageCond) {
        return queryFactory
                .selectFrom(member)
                .where(usernameEq(usernameCond),ageEq(ageCond))
                //.where(allEq(usernameCond,ageCond))
                .fetch();
    }

    private BooleanExpression ageEq(Integer ageCond) {
        return ageCond != null ? member.age.eq(ageCond) : null;
    }

    private BooleanExpression usernameEq(String usernameCond) {
        return usernameCond != null ? member.username.eq(usernameCond) : null;
    }

    private BooleanExpression allEq(String usernameCond, Integer ageCond){
        return usernameEq(usernameCond).and(ageEq(ageCond));
    }

    //수정,삭제 배치 쿼리 ( 별크 연산 )
    //쿼리 한번으로 대량 데이터 수정
    //변경감지로 인한 update 쿼리는 개별 엔티티 건 마다 쿼리가 나간다.
    //한번에 한 쿼리로 처리하는 경우 ex) 모든 개발자 연봉을 50%인상
    //이런 경우는 한번에 날려서 트랜잭션하는게 좋다
    @Test
    @Commit
    //벌크연산은 디비에 바로 꽂아버리기 때문에 영속성 컨텍스트에 있는
    //member들의 이름은 그대로 저장되어 있다.
    public void bulkUpdate(){

        long count = queryFactory
                .update(member)
                .set(member.username, "비회원")
                .where(member.age.lt(28)) //lt 미만
                .execute();

        em.flush();
        em.clear();

        //em
        //member1 = 10 -> member1
        //member2 = 20 -> member2
        //member3 = 30 -> member3
        //member4 = 40 -> member4

        //db
        //member1 = 10 -> 비회원
        //member2 = 20 -> 비회원
        //member3 = 30 -> member3
        //member4 = 40 -> member4

        List<String> result = queryFactory
                .select(member.username)
                .from(member)
                .fetch();

        //이렇게 member1 이름를 조회하면 디비에 있는 정보가 '비회원'이지만
        //영속성 컨텍스트에는 'member1'로 저장되어있어서 우선권을 가지는 영속성 컨텍스트의
        //정보인 'member1'이 출력되게 된다.
        //flush,clear 해주자
        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    public void bulkAdd(){
        long count = queryFactory
                .update(member)
                .set(member.age, member.age.add(1))
                .execute();
    }

    @Test
    public void bulkDelete(){
        long count = queryFactory
                .delete(member)
                .where(member.age.gt(18))
                .execute();
    }

    //SQL function 호출하기
    //SQL function은 JPA와 같이 Dialect에 등록된 내용만 호출할 수 있다.
    //MySQL은 yml파일에 설정해줘야한다.
    //replace 함수는 다음과 같이 3개의 인자를 받으며,
    //{0}, {1}, {2} 각각
    //m.username, member, M 이다.
    //replace('문자열' or 열이름, '바꾸려는 문자열','바뀔 문자열')
    @Test
    public void sqlFunction(){
        List<String> result = queryFactory
                .select(
                        Expressions.stringTemplate(
                                "function('regexp_replace', {0}, {1}, {2})",
                                member.username, "member", "M")) //이름을 M으로 바꿔서 조회
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    //소문자로
    @Test
    public void sqlFunction2(){
        queryFactory
                .select(member.username)
                .from(member)
                .where(member.username.eq(
                        Expressions.stringTemplate("function('lower', {0}",member.username)))
                .fetch();
    }



}




