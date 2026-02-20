package io.zhile.crack.atlassian.agent;

import javassist.*;

import java.io.File;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Objects;

/**
 * @author pengzhile
 * @link https://zhile.io
 * @version 1.0
 */
public class KeyTransformer implements ClassFileTransformer {
    private static final String CN_KEY_SPEC = "java/security/spec/EncodedKeySpec";
    private static final String CN_KEY_MANAGER = "com/atlassian/extras/keymanager/KeyManager";
    private static final String LICENSE_DECODER_PATH = "com/atlassian/extras/decoder/v2/Version2LicenseDecoder";
    private static final String LICENSE_DECODER_CLASS = "com.atlassian.extras.decoder.v2.Version2LicenseDecoder";

    private final String libPath;

    public KeyTransformer(String libPath) {
        this.libPath = libPath;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if (className == null) {
            return classfileBuffer;
        }

        if (className.equals(CN_KEY_SPEC)) {
            return handleKeySpec();
        }
        if (className.equals(CN_KEY_MANAGER) && libPath != null && !libPath.isEmpty()) {
            return handleKeyManager();
        }
        if (className.equals(LICENSE_DECODER_PATH) && libPath != null && !libPath.isEmpty()) {
            return handleLicenseDecoder();
        }

        return classfileBuffer;
    }

    private byte[] handleKeySpec() throws IllegalClassFormatException {
        try {
            ClassPool cp = ClassPool.getDefault();
            cp.importPackage("java.util.Arrays");
            cp.importPackage("javax.xml.bind.DatatypeConverter");

            int mod = Modifier.PRIVATE | Modifier.STATIC | Modifier.FINAL;
            CtClass cc = cp.get(CN_KEY_SPEC.replace('/', '.'));
            CtClass cb = cp.get("byte[]");
            CtField cfOld = new CtField(cb, "__h_ok", cc);
            CtField cfNew = new CtField(cb, "__h_nk", cc);
            cfOld.setModifiers(mod);
            cfNew.setModifiers(mod);
            cc.addField(cfOld, "DatatypeConverter.parseBase64Binary(\"MIIBuDCCASwGByqGSM44BAEwggEfAoGBAP1/U4EddRIpUt9KnC7s5Of2EbdSPO9EAMMeP4C2USZpRV1AIlH7WT2NWPq/xfW6MPbLm1Vs14E7gB00b/JmYLdrmVClpJ+f6AR7ECLCT7up1/63xhv4O1fnxqimFQ8E+4P208UewwI1VBNaFpEy9nXzrith1yrv8iIDGZ3RSAHHAhUAl2BQjxUjC8yykrmCouuEC/BYHPUCgYEA9+GghdabPd7LvKtcNrhXuXmUr7v6OuqC+VdMCz0HgmdRWVeOutRZT+ZxBxCBgLRJFnEj6EwoFhO3zwkyjMim4TwWeotUfI0o4KOuHiuzpnWRbqN/C/ohNWLx+2J6ASQ7zKTxvqhRkImog9/hWuWfBpKLZl6Ae1UlZAFMO/7PSSoDgYUAAoGBAIvfweZvmGo5otwawI3no7Udanxal3hX2haw962KL/nHQrnC4FG2PvUFf34OecSK1KtHDPQoSQ+DHrfdf6vKUJphw0Kn3gXm4LS8VK/LrY7on/wh2iUobS2XlhuIqEc5mLAUu9Hd+1qxsQkQ50d0lzKrnDqPsM0WA9htkdJJw2nS\");");
            cc.addField(cfNew, "DatatypeConverter.parseBase64Binary(\"MIIBuDCCASwGByqGSM44BAEwggEfAoGBAP1/U4EddRIpUt9KnC7s5Of2EbdSPO9EAMMeP4C2USZpRV1AIlH7WT2NWPq/xfW6MPbLm1Vs14E7gB00b/JmYLdrmVClpJ+f6AR7ECLCT7up1/63xhv4O1fnxqimFQ8E+4P208UewwI1VBNaFpEy9nXzrith1yrv8iIDGZ3RSAHHAhUAl2BQjxUjC8yykrmCouuEC/BYHPUCgYEA9+GghdabPd7LvKtcNrhXuXmUr7v6OuqC+VdMCz0HgmdRWVeOutRZT+ZxBxCBgLRJFnEj6EwoFhO3zwkyjMim4TwWeotUfI0o4KOuHiuzpnWRbqN/C/ohNWLx+2J6ASQ7zKTxvqhRkImog9/hWuWfBpKLZl6Ae1UlZAFMO/7PSSoDgYUAAoGBAO0DidNibJHhtgxAnM9NszURYU25CVLAlwFdOWhiUkjrjOY459ObRZDVd35hQmN/cCLkDox7y2InJE6PDWfbx9BsgPmPvH75yKgPs3B8pClQVkgIpJp08R59hoZabYuvm7mxCyDGTl2lbrOi0a3j4vM5OoCWKQjIEZ28OpjTyCr3\");");
            CtConstructor cm = cc.getConstructor("([B)V");
            cm.insertBeforeBody("if(Arrays.equals($1,__h_ok)){$1=__h_nk;System.out.println(\"============================== agent working ==============================\");}");

            return cc.toBytecode();
        } catch (Exception e) {
            throw new IllegalClassFormatException(e.getMessage());
        }
    }

    /**
     * Jira 9.12.25+: 公钥存储位置移到 KeyManager，通过重写 reset() 方法注入替换公钥
     */
    private byte[] handleKeyManager() throws IllegalClassFormatException {
        try {
            File libs = new File(libPath);
            if (!libs.exists() || !libs.isDirectory()) {
                System.err.println("atlassian-agent: invalid lib path for KeyManager: " + libPath);
                return null;
            }
            ClassPool cp = ClassPool.getDefault();
            Arrays.stream(Objects.requireNonNull(libs.listFiles())).map(File::getAbsolutePath).forEach((it) -> {
                try {
                    cp.insertClassPath(it);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            CtClass cc = cp.get(CN_KEY_MANAGER.replace('/', '.'));
            CtMethod resetMethod = cc.getDeclaredMethod("reset");
            String newMethodBody = "{\n" +
                    "    this.privateKeys.clear();\n" +
                    "    this.publicKeys.clear();\n" +
                    "    java.util.List keys = new java.util.ArrayList();\n" +
                    "    for(java.util.Iterator iter = this.env.entrySet().iterator(); iter.hasNext();) {\n" +
                    "        java.util.Map.Entry envVar = (java.util.Map.Entry) iter.next();\n" +
                    "        String envVarKey = (String)envVar.getKey();\n" +
                    "        if (envVarKey.startsWith(\"ATLAS_LICENSE_PRIVATE_KEY_\")) {\n" +
                    "            keys.add(new com.atlassian.extras.keymanager.Key((String)envVar.getValue(), extractVersion(envVarKey), com.atlassian.extras.keymanager.Key.Type.PRIVATE));\n" +
                    "        }\n" +
                    "        if (envVarKey.startsWith(\"ATLAS_LICENSE_PUBLIC_KEY_\")) {\n" +
                    "            keys.add(new com.atlassian.extras.keymanager.Key((String)envVar.getValue(), extractVersion(envVarKey), com.atlassian.extras.keymanager.Key.Type.PUBLIC));\n" +
                    "        }\n" +
                    "    }\n" +
                    "    for(java.util.Iterator it = keys.iterator(); it.hasNext();) {\n" +
                    "        com.atlassian.extras.keymanager.Key key = (com.atlassian.extras.keymanager.Key)it.next();\n" +
                    "        this.loadKey(key);\n" +
                    "    }\n" +
                    "    System.out.println(\"========= agent working (KeyManager) =========\");\n" +
                    "    this.loadKey(new com.atlassian.extras.keymanager.Key(\"MIIBuDCCASwGByqGSM44BAEwggEfAoGBAP1/U4EddRIpUt9KnC7s5Of2EbdSPO9EAMMeP4C2USZpRV1AIlH7WT2NWPq/xfW6MPbLm1Vs14E7gB00b/JmYLdrmVClpJ+f6AR7ECLCT7up1/63xhv4O1fnxqimFQ8E+4P208UewwI1VBNaFpEy9nXzrith1yrv8iIDGZ3RSAHHAhUAl2BQjxUjC8yykrmCouuEC/BYHPUCgYEA9+GghdabPd7LvKtcNrhXuXmUr7v6OuqC+VdMCz0HgmdRWVeOutRZT+ZxBxCBgLRJFnEj6EwoFhO3zwkyjMim4TwWeotUfI0o4KOuHiuzpnWRbqN/C/ohNWLx+2J6ASQ7zKTxvqhRkImog9/hWuWfBpKLZl6Ae1UlZAFMO/7PSSoDgYUAAoGBAIvfweZvmGo5otwawI3no7Udanxal3hX2haw962KL/nHQrnC4FG2PvUFf34OecSK1KtHDPQoSQ+DHrfdf6vKUJphw0Kn3gXm4LS8VK/LrY7on/wh2iUobS2XlhuIqEc5mLAUu9Hd+1qxsQkQ50d0lzKrnDqPsM0WA9htkdJJw2nS\", \"LICENSE_STRING_KEY_V2\", com.atlassian.extras.keymanager.Key.Type.PUBLIC));\n" +
                    "    this.loadKey(new com.atlassian.extras.keymanager.Key(\"MIIBtzCCASwGByqGSM44BAEwggEfAoGBAP1/U4EddRIpUt9KnC7s5Of2EbdSPO9EAMMeP4C2USZpRV1AIlH7WT2NWPq/xfW6MPbLm1Vs14E7gB00b/JmYLdrmVClpJ+f6AR7ECLCT7up1/63xhv4O1fnxqimFQ8E+4P208UewwI1VBNaFpEy9nXzrith1yrv8iIDGZ3RSAHHAhUAl2BQjxUjC8yykrmCouuEC/BYHPUCgYEA9+GghdabPd7LvKtcNrhXuXmUr7v6OuqC+VdMCz0HgmdRWVeOutRZT+ZxBxCBgLRJFnEj6EwoFhO3zwkyjMim4TwWeotUfI0o4KOuHiuzpnWRbqN/C/ohNWLx+2J6ASQ7zKTxvqhRkImog9/hWuWfBpKLZl6Ae1UlZAFMO/7PSSoDgYQAAoGALZHuJwQzgGnYm/X9BkMcewYQnWjMIGWHd9Yom5Qw7cVIdiZkqpiSzSKurO/WAHHLN31obg7NgGkitWUysECRE3zuJVbKGhx9xjVMnP6z5SwI89vB7Gn7UWxoCvT0JZgcMyQobXeVBtM9J3EgzkdDx/+Dck7uz/l1y+HDNdRzW00=\", \"1600708331\", com.atlassian.extras.keymanager.Key.Type.PUBLIC));\n" +
                    "}";
            resetMethod.setBody(newMethodBody);
            return cc.toBytecode();
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalClassFormatException(e.getMessage());
        }
    }


    /**
     * 移除用于验证哈希的方法: <code>com.atlassian.extras.decoder.v2.Version2LicenseDecoder#verifyLicenseHash</code>
     * 使用 agentArg 传入的 lib 路径，例如: -javaagent:atlassian-agent.jar=/path/to/atlassian-jira/WEB-INF/lib
     */
    private byte[] handleLicenseDecoder() throws IllegalClassFormatException {
        try {
            File libs = new File(libPath);
            if (!libs.exists()) {
                System.err.println("atlassian-agent: path [" + libPath + "] not found, please check agent arguments.");
                return null;
            }
            if (!libs.isDirectory()) {
                System.err.println("atlassian-agent: path [" + libPath + "] is not a directory, please check agent arguments.");
                return null;
            }
            ClassPool cp = ClassPool.getDefault();

            Arrays.stream(Objects.requireNonNull(libs.listFiles())).map(File::getAbsolutePath).forEach((it) -> {
                try {
                    cp.insertClassPath(it);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            cp.importPackage("com.atlassian.extras.common.LicenseException");
            cp.importPackage("com.atlassian.extras.common.org.springframework.util.DefaultPropertiesPersister");
            cp.importPackage("com.atlassian.extras.decoder.api.AbstractLicenseDecoder");
            cp.importPackage("com.atlassian.extras.decoder.api.LicenseVerificationException");
            cp.importPackage("com.atlassian.extras.keymanager.KeyManager");
            cp.importPackage("com.atlassian.extras.keymanager.SortedProperties");
            cp.importPackage("java.io.ByteArrayInputStream");
            cp.importPackage("java.io.ByteArrayOutputStream");
            cp.importPackage("java.io.DataInputStream");
            cp.importPackage("java.io.DataOutputStream");
            cp.importPackage("java.io.IOException");
            cp.importPackage("java.io.InputStream");
            cp.importPackage("java.io.InputStreamReader");
            cp.importPackage("java.io.OutputStream");
            cp.importPackage("java.io.Reader");
            cp.importPackage("java.io.StringWriter");
            cp.importPackage("java.io.Writer");
            cp.importPackage("java.nio.charset.Charset");
            cp.importPackage("java.nio.charset.StandardCharsets");
            cp.importPackage("java.text.SimpleDateFormat");
            cp.importPackage("java.util.Date");
            cp.importPackage("java.util.Map");
            cp.importPackage("java.util.Properties");
            cp.importPackage("java.util.zip.Inflater");
            cp.importPackage("java.util.zip.InflaterInputStream");
            cp.importPackage("org.apache.commons.codec.binary.Base64");

            CtClass target = cp.getCtClass(LICENSE_DECODER_CLASS);
            CtMethod verifyLicenseHash = target.getDeclaredMethod("verifyLicenseHash");
            verifyLicenseHash.setBody("{System.out.println(\"atlassian-agent: skip hash check\");}");

            return target.toBytecode();
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalClassFormatException(e.getMessage());
        }
    }

}
