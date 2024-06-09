package com.dbappsecurity.zerotrust.iam.resource.utils;



import com.dbappsecurity.zerotrust.constants.InvokeCode;
import com.dbappsecurity.zerotrust.domain.InvokeResult;



import com.dbappsecurity.zerotrust.iam.resource.manager.annotation.SysDatasourceParser;


import com.dbappsecurity.zerotrust.iam.resource.service.acct.AcctSourceScanService;
import com.dbappsecurity.zerotrust.iam.resource.service.acct.AcctSyncService;
import com.dbappsecurity.zerotrust.iam.resource.service.acct.common.BaseAcctBufferSyncServiceImpl;
import com.dbappsecurity.zerotrust.iam.resource.service.acct.common.BaseAcctSyncServiceImpl;
import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.web.multipart.MultipartFile;


import javax.crypto.Cipher;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Tool_GroovyShell {
    private static Logger logger = LoggerFactory.getLogger(Tool_GroovyShell.class);
    // groovy 类加载器
    private static GroovyClassLoader groovyClassLoader = null;

    private static PrivateKey privateKey = null;
    private static PublicKey publicKey = null;

    //记录账号源对应的文件集合路径
    public static  Map<String,List<String>> acctSourceFilePathMapping = new ConcurrentHashMap<>();
    //记录账号源对应的Class
    public static Map<String,Map<String,Class<?>>> acctSourceClassMapping = new ConcurrentHashMap<>();
    //记录账号源文件标签
    public static Map<String, List<String>> annotationMapping = new ConcurrentHashMap<>();
    private final static String rootPath = "/Users/avery/Desktop/groovy";
    private final static String AcctSourceScan = "AcctSourceScanService";
    private final static String AcctBufferSync = "AcctBufferSyncService";
    private final static String AcctSync = "AcctSyncService";

    static {
        KeyPair keyPair = RSAUtils.generateKeyPair();
        privateKey = keyPair.getPrivate();
        publicKey = keyPair.getPublic();
    }

    /**
     * 初始化groovy类加载器,加载所有的解析groovy文件
     */
    public static void initDatasourceConfig(){
        CompilerConfiguration config = new CompilerConfiguration();
        config.setSourceEncoding("UTF-8");
        ClassLoader parentClassLoader = Thread.currentThread().getContextClassLoader();
        groovyClassLoader = new GroovyClassLoader(parentClassLoader, config);
        try{
            initGroovyParserMapping();
        }catch (Exception e){
            logger.warn(e.toString());
        }
    }
    /**
     * 重新加载所有jar包
     */
    private static void initGroovyParserMapping() {
        groovyClassLoader.clearCache();
        acctSourceClassMapping = new ConcurrentHashMap<>();
        acctSourceFilePathMapping = new ConcurrentHashMap<>();
        annotationMapping = new ConcurrentHashMap<>();

        File folder = new File(rootPath);
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".jar"));
        if(files == null || files.length == 0){
            logger.info("文件夹下不存在待解析jar包");
            return;
        }
        for (File file : files) {
            logger.info(".....................解压"+file.getName()+"文件.....................");
            try {
                String acctName = file.getName();
                acctName = acctName.substring(0, acctName.lastIndexOf('.'));
                processJarFile(file);
                registerBeanIntoContext(acctName);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    /*
     * 解压jar包，Groovy解析至Class对象
     */
    private static void processJarFile(File jarFile) throws IOException {
        // 创建文件夹 filePath/sourceName
        String sourceName = jarFile.getName();
        sourceName = sourceName.substring(0, sourceName.lastIndexOf('.'));
        StringBuffer filePath = new StringBuffer();
        filePath.append(rootPath).append("/").append(sourceName);
        deleteFolder(filePath.toString());
        Path tempDir = Files.createDirectory(Paths.get(filePath.toString()));
        logger.info(".....................创建"+tempDir.toString()+"目录成功.....................");
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            List<File> fileList = new ArrayList<>();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.isDirectory()) {
                    File tempFile = extractFile(jar, entry, tempDir);
                    fileList.add(tempFile);
                }
            }
            processFiles(fileList,sourceName);
        }
    }
    /*
     *  解压到当前目录的新文件夹
     */
    private static File extractFile(JarFile jar, JarEntry entry, Path tempDir) throws IOException {
        Path filePath = tempDir.resolve(entry.getName());
        // 创建包含目标文件的所有父目录
        Files.createDirectories(filePath.getParent());
        try (InputStream inputStream = jar.getInputStream(entry)) {
            Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
        }
        return filePath.toFile();
    }
    /*
     * 解析Class对象
     */
    private static void processFiles(List<File> fileList,String sourceName){
        acctSourceFilePathMapping.put(sourceName,new ArrayList<>());
        acctSourceClassMapping.put(sourceName,new HashMap<>());
        annotationMapping.put(sourceName,new ArrayList<>());
        for (File file : fileList) {
            if(!file.getName().endsWith(".groovy"))continue;
            try {
                String fileName = file.getName();
                String annotation = "";
                if(fileName.contains(AcctSourceScan)){
                    Class<? extends AcctSourceScanService> parserClass = parseClass(file);
                    annotation = Tool_GroovyShell.getAnnotationInGroovyClass(parserClass);
                    acctSourceClassMapping.get(sourceName).put(annotation,parserClass);
                    annotationMapping.get(sourceName).add(annotation);
                }else if(fileName.contains(AcctBufferSync)){
                    Class<? extends BaseAcctBufferSyncServiceImpl> parserClass = parseClass(file);
                    annotation = Tool_GroovyShell.getAnnotationInGroovyClass(parserClass);
                    acctSourceClassMapping.get(sourceName).put(annotation,parserClass);
                    annotationMapping.get(sourceName).add(annotation);
                }else if(fileName.contains(AcctSync)){
                    Class<? extends BaseAcctSyncServiceImpl> parserClass = parseClass(file);
                    annotation = Tool_GroovyShell.getAnnotationInGroovyClass(parserClass);
                    acctSourceClassMapping.get(sourceName).put(annotation,parserClass);
                    annotationMapping.get(sourceName).add(annotation);
                }
                acctSourceFilePathMapping.get(sourceName).add(file.getPath());
                String infoTemplate = "加载数据源:%s-->groovy，文件:%s."; // 打印加载日志
                String info = String.format(infoTemplate, annotation, fileName);
                logger.info(info);
            } catch (Exception e) {
                String errorTemplate = "初始化加载Groovy文件失败!filename=%s";
                logger.warn(String.format(errorTemplate, file.getName()), e);
            }
        }
    }
    /*
     *  删除文件夹及文件夹内的所有文件
     */
    public static void deleteFolder(String dirPath) {
        if(!Files.exists(Paths.get(dirPath))){
            return;
        }
        File folder = new File(dirPath);
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteFolder(file.getPath());
                } else {
                    file.delete();
                }
            }
        }
        folder.delete();
    }
    /**
     * 根据groovy文件解析的Class构造Bean并注册到IOC容器
     * @param acctName 账号源前缀名
     */
    public static void registerBeanIntoContext(String acctName){
        logger.info(".....................加载和注册"+acctName+"数据源同步业务对象.....................");
        //1.从缓存中获取Class集合
        Class acctSyncServiceClass = null;
        Class acctBufferSyncServiceClass = null;
        Class acctSourceScanServiceClass = null;
        for(String annotation : annotationMapping.get(acctName)){
            if(annotation.equals(acctName+AcctSourceScan)){
                acctSourceScanServiceClass = acctSourceClassMapping.get(acctName).get(annotation);
            }else if(annotation.equals(acctName+AcctBufferSync)){
                acctBufferSyncServiceClass = acctSourceClassMapping.get(acctName).get(annotation);
            }else if(annotation.equals(acctName+AcctSync)){
                acctSyncServiceClass = acctSourceClassMapping.get(acctName).get(annotation);
            }
        }
        if(acctSyncServiceClass == null){
            logger.warn("账号源同步文件不全,缺acctSyncService文件");
            return;
        }else if(acctBufferSyncServiceClass == null){
            logger.warn("账号源同步文件不全,缺acctBufferSyncService文件");
            return;
        } else if(acctSourceScanServiceClass == null){
            logger.warn("账号源同步文件不全,缺acctSourceScanService文件");
            return;
        }
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
    }
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
    /*
     * 热部署
     * @param jarFile jar压缩文件
     */
    public static InvokeResult insertNewAcctSource(MultipartFile jarFile){
        //校验、解密、安装文件
        File file = ChecketOutAndEncryptFile(jarFile);
        if(file == null)return new InvokeResult(InvokeCode.FAIL.getCode(), "数据文件解密失败");;
        try {
            //解压加载文件
            processJarFile(file);
            String acctName = jarFile.getName();
            acctName = acctName.substring(0, acctName.lastIndexOf('.'));
            //注册对象
            registerBeanIntoContext(acctName);
        }catch (Exception e){
            logger.error("加载失败，原因：" + e.getMessage());
        }
        return new InvokeResult(InvokeCode.SUCCESS.getCode(), "数据源文件加载成功");
    }
    private static File ChecketOutAndEncryptFile(MultipartFile file){
        try{
            //校验文件后缀
            if(checkOutSuffix(file.getName())){
                logger.error("文件格式不正确");
                 return null;
            }
            //解密文件
            byte[] encryptedData = file.getBytes();
            Cipher rsaCipher = Cipher.getInstance("RSA");
            rsaCipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decryptedData = rsaCipher.doFinal(encryptedData);
            //创建临时文件并写入解密后的数据
            StringBuffer filePath = new StringBuffer();
            filePath.append(rootPath).append("/").append(file.getName());
            Path path = Paths.get(filePath.toString());
            if(Files.exists(path)){
                logger.error("文件已存在，执行删除操作.");
                Files.delete(path);
            }
            File tempFile = File.createTempFile(file.getName().split(".")[0],file.getName().split(".")[1]);
            FileOutputStream out = new FileOutputStream(tempFile);
            out.write(decryptedData);
            logger.info("文件"+tempFile.getAbsolutePath()+"安装成功!");
            out.close();
            return tempFile;
        }catch (Exception e){
            logger.error("文件解密安装失败",e.getMessage());
        }
        return null;
    }
    private static boolean checkOutSuffix(String fileName){
        //正则式匹配 是否满足xxx.jar
        String regex = "^[^\\s]+\\.jar$";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(fileName);
        return matcher.matches();
    }
    /*
     *  输出类的所有信息(测试用)
     */
    public static void printClassInfo(Object obj) {
        if (obj == null) {
            System.out.println("The object is null.");
            return;
        }
        Class<?> clazz = obj.getClass();
        System.out.println("Class Name: " + clazz.getName());
        // 打印类的修饰符
        int modifiers = clazz.getModifiers();
        System.out.println("Class Modifiers: " + Modifier.toString(modifiers));
        // 打印类的实现的接口
        Class<?>[] interfaces = clazz.getInterfaces();
        if (interfaces.length > 0) {
            System.out.println("Implemented Interfaces:");
            for (Class<?> iface : interfaces) {
                System.out.println(" - " + iface.getName());
            }
        }
        // 打印类的父类
        Class<?> superClass = clazz.getSuperclass();
        if (superClass != null) {
            System.out.println("Super Class: " + superClass.getName());
        }
        // 打印类的构造函数
        Constructor<?>[] constructors = clazz.getDeclaredConstructors();
        if (constructors.length > 0) {
            System.out.println("Constructors:");
            for (Constructor<?> constructor : constructors) {
                System.out.println(" - " + constructor.toString());
            }
        }
        // 打印类的字段
        Field[] fields = clazz.getDeclaredFields();
        if (fields.length > 0) {
            System.out.println("Fields:");
            for (Field field : fields) {
                System.out.println(" - " + field.toString());
            }
        }
        // 打印类的方法
        Method[] methods = clazz.getDeclaredMethods();
        if (methods.length > 0) {
            System.out.println("Methods:");
            for (Method method : methods) {
                System.out.println(" - " + method.toString());
            }
        }
    }
    /*
     * 传入获取注解内容
     */
    public static String getAnnotationInGroovyClass(Class<?> clazz) {
        if (clazz != null) {
            SysDatasourceParser sysDatasourceParser = clazz.getAnnotation(SysDatasourceParser.class);
            if (sysDatasourceParser != null) {
                return sysDatasourceParser.value();
            }
        }
        return null;
    }
    /**
     * 传入file对象,返回groovy的class对象.
     */
    public static Class parseClass(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return null;
        } else {
            try {
                return groovyClassLoader.parseClass(file);
            } catch (IOException e) {
                logger.warn("解析文件成groovy异常，path=" + file.getPath(), e);
                return null;
            }
        }
    }
}

