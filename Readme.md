Arete AEM Components
--------------------

Command to setup this project:

```
mvn -B org.apache.maven.plugins:maven-archetype-plugin:3.3.1:generate \
 -D archetypeGroupId=com.adobe.aem \
 -D archetypeArtifactId=aem-project-archetype \
 -D archetypeVersion=56\
 -D appTitle="Adobe XP Components" \
 -D appId="adobexp" \
 -D artifactId="adobexp" \
 -D aemVersion="6.5.22" \
 -D frontendModule="none" \
 -D datalayer="n" \
 -D groupId="com.adobexp.aem"
```