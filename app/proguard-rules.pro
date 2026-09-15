# R8 no release. As bibliotecas (Compose, AndroidX, coroutines) ja trazem as
# proprias regras; o que precisa de cuidado aqui e a serializacao do protocolo.
#
# kotlinx.serialization gera um `$$serializer` e um `Companion.serializer()`
# para cada @Serializable. Sao alcancados por nome, nao por chamada direta:
# sem estas regras o R8 os remove e a sala cai com "Serializer not found" -
# justamente no caminho que so aparece com dois aparelhos conectados.

-keepattributes *Annotation*, InnerClasses, Signature

-keepclassmembers class fodinha.** {
    *** Companion;
}
-keepclasseswithmembers class fodinha.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class fodinha.**$$serializer { *; }

# As proprias classes do protocolo e do estado do jogo: o discriminador "t" do
# JSON carrega o nome da classe, entao renomear muda o que viaja no socket e
# duas versoes do app deixam de se entender.
-keep @kotlinx.serialization.Serializable class fodinha.** { *; }
