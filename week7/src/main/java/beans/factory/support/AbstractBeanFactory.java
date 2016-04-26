package beans.factory.support;

import beans.PropertyValue;
import beans.PropertyValues;
import beans.factory.BeanCurrentlyInCreationException;
import beans.factory.BeanFactory;
import beans.factory.config.BeanDefinition;
import beans.factory.config.BeanPostProcessor;
import core.*;
import org.apache.log4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractBeanFactory implements BeanFactory {

    // '생성중' 이라는 마킹을 위해 선언된 변수
    private static final Object CURRENTLY_IN_CREATION = new Object();
    private final BeanFactory parentBeanFactory;
    private final List<BeanPostProcessor> beanPostProcessors = new ArrayList<>();
    private Logger log = Logger.getLogger(this.getClass().getName());
    /**
     * Map of Bean objects, keyed by id attribute
     */
    private Map beanHash = new HashMap();

    public AbstractBeanFactory(BeanFactory parentBeanFactory) {
        this.parentBeanFactory = parentBeanFactory;
    }

    public abstract BeanDefinition getBeanDefinition(String key);

    public <T> T getBean(String key, Class<T> clazz) {
        return (T)getBean(key);
    }

    public Object getBean(String key) {
        return getBeanInternal(key);
    }

    private Object getBeanInternal(String name) {

        if (name == null)
            throw new IllegalArgumentException("Bean name null is not allowed");

        if (beanHash.containsKey(name)) {
            Object object = beanHash.get(name);
            if (object == CURRENTLY_IN_CREATION) {
                throw new BeanCurrentlyInCreationException("current bean name: " + name);
            }
            if (log.isDebugEnabled()) {
                log.debug("returning cached object: " + name);
            }
            return object;
        } else {
            BeanDefinition beanDefinition = getBeanDefinition(name);
            if (beanDefinition != null) {
                this.beanHash.put(name, CURRENTLY_IN_CREATION);
                log.debug("creating instance: " + name);
                Object newlyCreatedBean = createBean(beanDefinition, name);
                beanHash.put(name, newlyCreatedBean);
                return newlyCreatedBean;
            } else {
                if (this.parentBeanFactory == null)
                    throw new IllegalArgumentException(
                        "Cannot instantiate [bean name : " + name + "]; is not exist");
                return parentBeanFactory.getBean(name);
            }
        }
    }

    private Object createBean(BeanDefinition beanDefinition, String beanName) {
        try {
            //BeanDefinition beanDefinition = getBeanDefinition(key);
            ConstructorArguments constructorArguments = beanDefinition.getConstructorArguments();
            PropertyValues propertyValues = beanDefinition.getPropertyValues();

            Object newlyCreatedBean;

            ConstructorArgument[] argsArray = constructorArguments.getConstructorArguments();

            if (argsArray.length > 0) {

                int argsSize = argsArray.length;

                Class[] constructorArgs = new Class[argsSize];
                Object[] instanceArgs = new Object[argsSize];

                for (int i = 0; i < argsSize; i++) {
                    Object argBean = getBean(argsArray[i].getRefId());
                    constructorArgs[i] = argBean.getClass();
                    instanceArgs[i] = argBean;
                }

                Constructor constructor = beanDefinition.getBeanClass().getConstructor(constructorArgs);
                newlyCreatedBean = constructor.newInstance(instanceArgs);
            } else {
                newlyCreatedBean = beanDefinition.getBeanClass().newInstance();
            }

            applyPropertyValues(beanDefinition, propertyValues, newlyCreatedBean, beanName);
            newlyCreatedBean = callLifecycleMethodsIfNecessary(newlyCreatedBean, beanName);// Bean Life Cycle 처리 메소드 호출

            return newlyCreatedBean;
        } catch (InstantiationException e) {
            e.printStackTrace();
            throw new IllegalArgumentException(
                "Cannot instantiate [bean name : " + beanName + "]; is it an interface or an abstract class?");
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            throw new IllegalArgumentException("Cannot instantiate [bean name : " + beanName
                + "]; has class definition changed? Is there a public constructor?");
        } catch (NoSuchMethodException | InvocationTargetException e) {
            e.printStackTrace();
            throw new IllegalArgumentException("Cannot instantiate [bean name: " + beanName + "]");
        }
    }

    private void applyPropertyValues(BeanDefinition beanDefinition,
                                     PropertyValues propertyValues,
                                     Object bean,
                                     String beanName) {
        Class clazz = beanDefinition.getBeanClass();

        PropertyValue[] array = propertyValues.getPropertyValues();
        for (int i = 0; i < propertyValues.getCount(); ++i) {
            PropertyValue property = array[i];
            try {
                Field field = clazz.getDeclaredField(property.getName());
                String propertyName = property.getName();

                Method method = clazz.getMethod(
                    "set" + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1),
                    new Class[] {field.getType()});

                //Integer?? String ?��?��?�� ???��?���? ?��?��
                if ("java.lang.Integer".equals(field.getType().getName())) {
                    method.invoke(bean, Integer.parseInt(property.getValue().toString()));
                } else {
                    method.invoke(bean, property.getValue().toString());
                }
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
                throw new IllegalArgumentException("Cannot instantiate [bean name : " + beanName
                    + "]; is not have field [" + property.getName() + "]");
            } catch (IllegalAccessException e) {
                e.printStackTrace();
                throw new IllegalArgumentException("Cannot instantiate [bean name : " + beanName
                    + "]; Cannot access field [" + property.getName() + "]");
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
                throw new IllegalArgumentException("Cannot instantiate [bean name : " + beanName
                    + "]; Cannot access field, set method not defined [" + property.getName() + "]");
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        }
    }

    private Object callLifecycleMethodsIfNecessary(Object bean, String beanName) {
        //        if (bean instanceof step4.InitializingBean) {
        //            ((step4.InitializingBean) bean).afterPropertiesSet();
        //        }

        // 1. BeanNameAware's setBeanName
        // Todo: bean이 BeanNameAware 인터페이스를 구현한 경우 처리, beanName을 bean의 구현 메소드(setBeanName)에 넘겨준다.

        // 2. BeanFactoryAware's setBeanFactory
        // Todo: bean이 BeanFactoryAware 인터페이스를 구현한 경우 처리, 현재 beanFactory(this)를 bean의 구현 메소드(setBeanName)에 넘겨준다.

        // 4. postProcessBeforeInitialization methods of BeanPostProcessors
        // Todo: BeanPostProcessor 인터페이스를 구현한 클래스가 등록되어 있는 경우 처리 (postProcessorBeforeInitialization)

        // 5. InitializingBean's afterPropertiesSet
        // Todo: bean이 InitializingBean 인터페이스를 구현한 경우 처리, bean의 구현 메소드(afterPropertiesSet)를 호출한다.

        // 4. postProcessBeforeInitialization methods of BeanPostProcessors
        // Todo: BeanPostProcessor 인터페이스를 구현한 클래스가 등록되어 있는 경우 처리 (postProcessorAfterInitialization)

        return bean;
    }

    public void destroyBean(String beanName, Object bean) {
        // 6. DisposableBean's destroy
        // Todo:  bean이 DisposableBean 인터페이스를 구현한 경우 처리
    }

    public void addBeanPostProcessor(BeanPostProcessor beanPostProcessor) {
        this.beanPostProcessors.add(beanPostProcessor);
    }
}