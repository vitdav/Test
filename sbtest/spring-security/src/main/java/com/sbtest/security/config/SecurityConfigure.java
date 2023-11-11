package com.sbtest.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sbtest.security.filter.LoginFilter;
import com.sbtest.security.service.MyRememberMeServices;
import com.sbtest.security.service.MyUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.session.SessionInformationExpiredEvent;
import org.springframework.security.web.session.SessionInformationExpiredStrategy;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


@Configuration
public class SecurityConfigure extends WebSecurityConfigurerAdapter {
    //1. 注入自定义的数据源 DetailsService
    private final MyUserDetailsService myUserDetailsService;
    private final DataSource dataSource;


    @Autowired
    public SecurityConfigure(MyUserDetailsService myUserDetailsService, DataSource dataSource) {
        this.myUserDetailsService = myUserDetailsService;
        this.dataSource = dataSource;
    }

    //2.自定义 AuthenticationManager，覆盖默认的
    @Override
    protected void configure(AuthenticationManagerBuilder builder) throws Exception {
        builder.userDetailsService(myUserDetailsService);
    }

    //3. 将自定义的AuthenticationManager 暴漏出去
    @Bean // 将自定义的AuthenticationManager 暴漏出去
    @Override
    protected AuthenticationManager authenticationManager() throws Exception {
        return super.authenticationManager();
    }

    // 4. 将自定义的Filter交给IOC
    @Bean
    public LoginFilter loginFilter() throws Exception {
        LoginFilter loginFilter = new LoginFilter();

        //1. 这里可以选择手动指定用户名、密码和验证码的参数名，让前端传参更灵活
        loginFilter.setUsernameParameter("uname"); //指定接收json用户名的key
        loginFilter.setPasswordParameter("pwd");//指定接收json密码的key
        loginFilter.setKaptchaKey("kaptcha");
        //设置认证成功时使用的自定义rememberMeService
        loginFilter.setRememberMeServices(rememberMeServices());

        //2. 注入自定义的AuthenticationManager
            //调用暴漏自定义AuthenticationManager的方法，进行获取
        loginFilter.setAuthenticationManager(authenticationManager());

        // 3. 指定验证成功和失败的Handler，
        //替代的是 HttpSecurity的successHandler和failureHandler配置
            //3.1 认证成功的处理
        loginFilter.setAuthenticationSuccessHandler(new MyAuthenticationSuccessHandler());
            //3.2 认证失败的处理
        loginFilter.setAuthenticationFailureHandler(new MyAuthenticationFailureHandler());

        //4. 指定认证的URL
        loginFilter.setFilterProcessesUrl("/doLogin");


        return loginFilter;
    }

    //2.指定数据库持久化
    @Bean
    public PersistentTokenRepository jdbcToken(){
        //基于数据库实现，使用JdbcTokenRepository替代默认的内存实现
        JdbcTokenRepositoryImpl jdbcToken = new JdbcTokenRepositoryImpl();
        //指定数据源
        jdbcToken.setDataSource(dataSource);
        //创建表结构，第一次新建表结构时设置为true，第二次后要手动改为false
        jdbcToken.setCreateTableOnStartup(false);

        return jdbcToken;
    }


    //5. 自定义RememberMeServices记住我实现
    public RememberMeServices rememberMeServices(){
        return new MyRememberMeServices(
                UUID.randomUUID().toString(),
                userDetailsService(),
                jdbcToken()
        );
    }



    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests()
                .mvcMatchers("/vc").permitAll()
                .anyRequest().authenticated()
                .and().formLogin()

                .and().rememberMe()
                // .tokenRepository(jdbcToken())
                //设置自动登录使用哪个rememberMeServices
                .rememberMeServices(rememberMeServices())

                .and()
                .exceptionHandling()
                .authenticationEntryPoint(new MyAuthenticationEntryPoint())

                //注销还是在这里进行配置
                .and().logout()// 手动开启注销
                .logoutUrl("/logout") // 手动指定注销的url，默认是 `logout`,且为get请求
                .logoutSuccessHandler(new MyLogoutSuccessHandler())
                .and().csrf().disable()

                .sessionManagement() //开启会话管理
                .maximumSessions(1) //设置运行会话的最大并发数
                // .expiredUrl("/login")
                .expiredSessionStrategy(event -> {
                            HttpServletResponse response = event.getResponse();
                            // map转json进行输出
                            Map<String, Object> result = new HashMap<>();
                            result.put("status", 500);
                            result.put("msg", "当前会话已经失效,请重新登录!");
                            String s = new ObjectMapper().writeValueAsString(result);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().println(s);
                            response.flushBuffer();
                    }
                );


        //扩展过滤器
        // 1.addFilterAtt：将一个Filter，替换过滤器链中的某个Filter
        // 2.before：将一个Filter，放在过滤器链中某个Filter之前
        // 2.after：将一个Filter，放在过滤器链中某个Filter之后
        http.addFilterAt(loginFilter(), UsernamePasswordAuthenticationFilter.class);


    }

}


