# Configurações, histórico e cópias de segurança são gravados em JSON com
# kotlinx.serialization. As regras abaixo mantêm os serializadores gerados de
# todas as classes @Serializable do app (modelos, cópia de configurações e a
# escolha de som que passa entre telas).
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.**

-keep,includedescriptorclasses class com.plantaoseguro.**$$serializer { *; }
-keepclassmembers class com.plantaoseguro.** {
    *** Companion;
}
-keepclasseswithmembers class com.plantaoseguro.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Objetos (data object) e subclasses seladas serializáveis
-keepclassmembers @kotlinx.serialization.Serializable class com.plantaoseguro.** {
    static ** INSTANCE;
}
