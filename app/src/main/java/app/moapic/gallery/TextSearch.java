package app.moapic.gallery;

public final class TextSearch {
    private TextSearch(){}
    public static boolean matches(String content,String query){
        String normalized=MediaClassifier.normalize(content),q=MediaClassifier.normalize(query).trim();
        if(q.isEmpty())return true;
        for(String token:q.split("\\s+")){
            if(normalized.contains(token))continue;
            if(token.matches("[0-9]+")&&normalized.replaceAll("(?<=\\d)[\\s,.-]+(?=\\d)","").contains(token))continue;
            return false;
        }
        return true;
    }
}
