package com.yumi.baseusage;

public class JsonTest {

    public static class BaseDao {

        private String getNameSpace() {
            return getClass().getName();
        }

        protected String getStatement(String sqlId) {
            return getNameSpace() + "/" + sqlId;
        }

        public void cc() {
            System.out.println(getStatement("cc"));
        }
    }


    public static class DaoImpl extends BaseDao {

       public void aa() {
           System.out.println(getStatement("aa"));
       }

       public void bb() {
           cc();
       }
    }

    static void main() {
        BaseDao dao = new DaoImpl();
        dao.cc();
    }
}
