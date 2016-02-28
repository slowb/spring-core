package bean;

import step5.InitializingBean;

public class E implements InitializingBean {
    public long createdAt;

    @Override
    public void afterPropertiesSet() {
        createdAt = System.currentTimeMillis();
        System.out.println(getClass().getName() + " is created at " + createdAt);
    }
}
