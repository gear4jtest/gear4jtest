//package io.github.gear4jtest.core.aspectj;
//
//import org.aspectj.lang.JoinPoint;
//import org.aspectj.lang.annotation.Aspect;
//import org.aspectj.lang.annotation.Before;
//
//@Aspect
//public class AccountAspect2 {
//	
//    @Before("execution(public boolean io.github.gear4jtest.core.aspectmodel.Account.withdraw(..))")
//    public void logEnter(JoinPoint joinPoint) {
//        System.out.print(joinPoint.getStaticPart());
//        System.out.print(" -> YO GROS ");
//        System.out.println(joinPoint.getSignature());
//    }
//	
//}