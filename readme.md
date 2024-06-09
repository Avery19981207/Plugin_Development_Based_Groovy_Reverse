# 动态Bean注册Groovy逆向解析插件加载器
---

## **简介**

该项目提供了一个基于Groovy的插件化开发框架，旨在实现动态加载Groovy脚本并将其注册为Spring应用程序上下文中的Bean。这使得您可以轻松地扩展和定制应用程序的功能，而无需修改主应用程序代码。
## Groovy 编译机制
Groovy 代码会被编译成 Java 字节码，并通过 GroovyClassLoader 进行加载。
GroovyClassLoader 是 Java 类加载器的一个扩展，它可以加载 Groovy 类，并将其编译成 Java 类。

---

## **主要功能**

1. **动态插件加载：** 通过GroovyClassLoader从jar文件中动态加载Groovy脚本，实现了热插拔和运行时更新的功能。

2. **灵活的Bean注册：** Groovy脚本中定义的Bean会被注册到Spring应用程序上下文中，实现了与现有应用程序架构的无缝集成。

3. **基于注解的配置：** 插件使用`@SysDatasourceParser`注解进行标注，便于对插件进行分类和管理。

4. **安全部署：** 在处理jar文件之前，通过RSA加密算法对其进行安全解密，确保数据的完整性和安全性。

---
## 项目场景 
账号源对接模块，针对定制化的账号源需要实现三个业务类：acctSourceScanService、acctBufferSyncService和acctSyncService。
**acctSourceScanService.groovy**：完成组织、用户数据同步至本地缓冲表
**acctBufferSyncService.groovy**：完成组织、用户数据从本地缓冲表同步至数据库
**acctSyncService.groovy**：提供上述两类服务给应用接口
| 插件名称             | 依赖关系                       |
|---------------------|--------------------------------|
| acctSourceScanService | 无依赖                       |
| acctSyncService       | acctSourceScanService, acctBufferSyncService |
| acctBufferSyncService | acctSourceScanService          |

## **使用指南**

1. **安装**

   - 将存储库克隆到本地计算机。
   - 确保您的计算机上已经安装了JDK、Maven和Spring Framework等必要的开发工具。

2. **配置**

   - 在`Tool_GroovyShell.java`文件中，根据您的需求修改`rootPath`变量，指定Groovy脚本所在的目录路径。

3. **使用**

   - 编写您的Groovy脚本，并确保在需要注册为Bean的类上使用`@SysDatasourceParser`注解进行标注。
   - 将编写好的Groovy脚本打包成jar文件，并将其放置在指定的目录中。
   - 启动Spring应用程序，插件将自动加载并注册到应用程序上下文中，您可以通过应用程序的功能菜单或API来使用这些插件功能。

---

## **安全性考虑**

- 使用RSA加密算法对jar文件进行解密，防止恶意篡改和数据泄露。
- 通过文件后缀和正则表达式校验确保文件格式正确性。

---

## 问题整理

### 问题1：

#### 	使用ApplicationContext类自动导入了Spring1.2.6中的ApplicationContext类，类中没有提供getAutowireCapableBeanFactory()等方法。应该使用的是Spring-context5.3.25中的ApplicationContext，但是import不了这个类。

### 原因：

​	项目中org.codehaus.xfire.xfire-all和org.springframework.spring有依赖冲突。org.codehaus.xfire.xfire-all包含了org.springframework.spring的依赖文件。在使用ApplicationContext对象时，由于ApplicationContext在org.springframework.spring-context中的路径跟org.springframework.spring中该类的路径一样，maven优先获取了该类，因此context中的这个类识别不出来。

```xml
				<dependency>
            <groupId>org.codehaus.xfire</groupId>
            <artifactId>xfire-all</artifactId>
            <version>1.2.6</version>
        </dependency>
```

### 解决：

​	org.codehaus.xfire.xfire-all获取依赖时排除掉关于org.springframework.spring的依赖，使得org.springframework.spring只保留一份。

```xml
				<dependency>
            <groupId>org.codehaus.xfire</groupId>
            <artifactId>xfire-all</artifactId>
            <version>1.2.6</version>
            <exclusions>
                <exclusion>
                    <groupId>org.springframework</groupId>
                    <artifactId>spring</artifactId>
                </exclusion>
            </exclusions>
        </dependency>
```

### 问题2：

#### 	将acctSourceScanService、acctSyncService、acctBufferSyncService三个类成功加载成Class对象后进行手动创建bean并注册到IOC容器时，出现`NoSuchBeanDefinitionException` 异常。

### 原因：

​	三个类有严格的各自注入关系，acctSourceScanService没有另外两者作依赖注入；acctBufferSyncService需要acctSourceScanService作为依赖注入；acctSyncService需要acctSourceScanService和acctBufferSyncService作为依赖注入。开始没有考虑创建bean的顺序，因此优先创建后面的bean时注入发现没有对应的bean。

### 解决：

​	严格按照顺序注册和创建bean

```java
//2.严格按照下面顺序依次加载bean
        logger.info("................loading acctSourceScanServiceClass............");
        if(!createBeanIntoIOC(acctSourceScanServiceClass,acctName+AcctSourceScan)){
            logger.info("loading acctSourceScanServiceClass fails.");
            return;
        }
        logger.info("................loading acctSourceScanServiceClass successfully!............");
        logger.info("................loading acctBufferSyncServiceClass............");
        if(!createBeanIntoIOC(acctBufferSyncServiceClass,acctName+AcctBufferSync)){
            logger.info("loading acctBufferSyncServiceClass fails.");
            return;
        }
        logger.info("................loading acctBufferSyncServiceClass successfully!............");
        logger.info("................loading acctSyncServiceClass............");
        if(!createBeanIntoIOC(acctSyncServiceClass,acctName+AcctSync)){
            logger.info("loading acctSyncServiceClass fails.");
            return;
        }
        logger.info("................loading acctBufferSyncServiceClass successfully!............");
```

### 问题3：

#### 按顺序注册后，依然出现NoSuchBeanDefinitionException异常。

### 原因：

```java
private static boolean createBeanIntoIOC(Class target,String beanName) {
        try{
            //1.获取ApplicationContext上下文
            ApplicationContext context = SpringContextUtils.getApplicationContext();
            //2.使用AutowireCapableBeanFactory 完成bean构造、自动注入、初始化、注册
            AutowireCapableBeanFactory autowireCapableBeanFactory = context.getAutowireCapableBeanFactory();
            //3.构造bean
            Object bean = autowireCapableBeanFactory.createBean(target,AutowireCapableBeanFactory.AUTOWIRE_BY_TYPE, false);
            if(bean == null){
                logger.warn("构造"+beanName+"的bean失败");
                return false;
            }
            return true;
        }catch (Exception e){
            String errorTemplate = "构建"+beanName+"的bean注入IOC失败: ";
            logger.warn(errorTemplate + e);
            e.printStackTrace();
        }
        return false;
    }
```

​	查看createBean源码，发现调用createBean只是绑定了Class和Bean的关联，注册了bean之后可以通过context.getBean(xxx.class)来获取bean，但是在本业务中，xxx.class是动态加载的之后才有的，并不是写死在原本的插件代码的Autowire中，因此不可行。

​    用createBean虽然成功IOC中注入了该Bean对象，但是后续无法通过字符串beanName去获取对象，因此@Autowire尽管加了@Qualifier("beanName")注解来指定注入beanName名称的bean，但是实际上不存在名称为beanName的bean，因此注入发生NoSuchBeanDefinitionException异常。

### 解决：

```java
private static boolean createBeanIntoIOC(Class target,String beanName) {
        try{
            //1.获取ApplicationContext上下文
            ApplicationContext context = SpringContextUtils.getApplicationContext();
            //2.使用AutowireCapableBeanFactory 完成bean构造、自动注入、初始化、注册
            AutowireCapableBeanFactory autowireCapableBeanFactory = context.getAutowireCapableBeanFactory();
            //3.构造bean
            Object bean = autowireCapableBeanFactory.createBean(target,AutowireCapableBeanFactory.AUTOWIRE_BY_TYPE, false);
            if(bean == null){
                logger.warn("构造"+beanName+"的bean失败");
                return false;
            }
            //4.初始化bean
            autowireCapableBeanFactory.initializeBean(bean, beanName);
            //5.注册并命名bean
            ((DefaultListableBeanFactory) autowireCapableBeanFactory).registerSingleton(beanName, bean);
            return true;
        }catch (Exception e){
            String errorTemplate = "构建"+beanName+"的bean注入IOC失败: ";
            logger.warn(errorTemplate + e);
            e.printStackTrace();
        }
        return false;
    }
```

在构造bean之后，调用autowireCapableBeanFactory的registerSingleton(beanName, bean);方法手动注册命名bean；这个方法是autowireCapableBeanFactory接口的一个特殊实例DefaultListableBeanFactory独有的，需要强制转型来调用。

同时，为了保证后续插件的bean对象自定义实现 `InitializingBean` 和 `DisposableBean` 接口，自定义写了initMethod、destoryMethod等等，也就是创建bean之前的初始化操作以及销毁bean的操作，需要手动调用autowireCapableBeanFactory.initializeBean(bean)来执行这些初始化步骤。

因此最后的步骤为：

#### **<u>获取ApplicationContext->获取autowireCapableBeanFactory->构造bean->初始化bean->注册命名bean</u>**


