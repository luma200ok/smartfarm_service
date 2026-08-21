import type { Metadata } from "next";
import FarmMembers from "@/components/farms/FarmMembers";

export const metadata: Metadata = {
  title: "멤버 | 스마트팜",
};

export default async function FarmMembersPage(props: PageProps<"/farms/[farmId]/members">) {
  const { farmId } = await props.params;
  return <FarmMembers farmId={farmId} />;
}
